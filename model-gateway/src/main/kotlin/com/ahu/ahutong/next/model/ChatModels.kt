package com.ahu.ahutong.next.model

import com.ahu.ahutong.next.agent.ChatModel
import com.ahu.ahutong.next.agent.LlmMessage
import com.ahu.ahutong.next.agent.ModelReply
import com.ahu.ahutong.next.agent.ModelStreamEvent
import com.ahu.ahutong.next.agent.ToolCall
import com.ahu.ahutong.next.agent.ToolResult
import com.ahu.ahutong.next.agent.ToolSpec
import com.ahu.ahutong.next.agent.AgentJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class OpenAiCompatibleChatModel(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) : ChatModel {

    override val name: String = model

    override suspend fun complete(messages: List<LlmMessage>, tools: List<ToolSpec>): ModelReply =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "API Key 为空" }

            val request = buildRequest(messages, tools, stream = false)
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("模型接口返回 ${response.code}：${body.take(300)}")
                }
                val parsed = runCatching {
                    AgentJson.decodeFromString(CompletionResponse.serializer(), body)
                }.getOrElse { throw IOException("模型响应无法解析：${body.take(300)}") }

                val message = parsed.choices.firstOrNull()?.message
                    ?: throw IOException("模型没有返回候选结果")

                ModelReply(
                    content = message.content,
                    toolCalls = message.toolCalls.orEmpty().map { wire ->
                        ToolCall(
                            id = wire.id.ifBlank { UUID.randomUUID().toString() },
                            name = wire.function.name,
                            arguments = runCatching {
                                AgentJson.parseToJsonElement(wire.function.arguments).jsonObject
                            }.getOrElse { buildJsonObject { } },
                        )
                    },
                )
            }
        }

    override fun stream(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
    ): Flow<ModelStreamEvent> = flow {
        require(apiKey.isNotBlank()) { "API Key 为空" }

        val request = buildRequest(messages, tools, stream = true)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IOException("模型接口返回 ${response.code}：${body.take(300)}")
            }

            val source = response.body?.source() ?: throw IOException("模型流为空")
            val content = StringBuilder()
            val toolCalls = sortedMapOf<Int, StreamToolAccumulator>()

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val chunk = runCatching {
                    AgentJson.decodeFromString(StreamChunk.serializer(), data)
                }.getOrNull() ?: continue
                val delta = chunk.choices.firstOrNull()?.delta ?: continue

                delta.content?.takeIf { it.isNotEmpty() }?.let {
                    content.append(it)
                    emit(ModelStreamEvent.Delta(it))
                }

                delta.toolCalls?.forEach { call ->
                    val index = call.index ?: 0
                    val acc = toolCalls.getOrPut(index) { StreamToolAccumulator() }
                    call.id?.takeIf { it.isNotEmpty() }?.let { acc.id = it }
                    call.function?.name?.takeIf { it.isNotEmpty() }?.let { acc.name = it }
                    call.function?.arguments?.let { acc.arguments.append(it) }
                }
            }

            val calls = toolCalls.values.mapNotNull { acc ->
                if (acc.name.isBlank()) return@mapNotNull null
                ToolCall(
                    id = acc.id.ifBlank { UUID.randomUUID().toString() },
                    name = acc.name,
                    arguments = runCatching {
                        AgentJson.parseToJsonElement(acc.arguments.toString()).jsonObject
                    }.getOrElse { buildJsonObject { } },
                )
            }

            emit(
                ModelStreamEvent.Completed(
                    ModelReply(
                        content = content.toString().ifBlank { null },
                        toolCalls = calls,
                    ),
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequest(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        stream: Boolean,
    ): Request {
        val payload = buildJsonObject {
            put("model", model)
            put("messages", AgentJson.encodeToJsonElement(messages))
            if (tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        tools.forEach { spec ->
                            add(
                                buildJsonObject {
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", spec.name)
                                            put("description", spec.description)
                                            put("parameters", spec.parameters)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
                put("tool_choice", "auto")
            }
            put("temperature", 0.2)
            put("stream", stream)
        }

        return Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }
}

@Serializable
private data class CompletionResponse(
    val choices: List<CompletionChoice> = emptyList(),
)

@Serializable
private data class CompletionChoice(
    val message: CompletionMessage? = null,
)

@Serializable
private data class CompletionMessage(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<CompletionToolCall>? = null,
)

@Serializable
private data class CompletionToolCall(
    val id: String = "",
    val type: String = "function",
    val function: CompletionFunctionCall = CompletionFunctionCall(),
)

@Serializable
private data class CompletionFunctionCall(
    val name: String = "",
    val arguments: String = "{}",
)

@Serializable
private data class StreamChunk(
    val choices: List<StreamChoice> = emptyList(),
)

@Serializable
private data class StreamChoice(
    val delta: StreamDelta? = null,
)

@Serializable
private data class StreamDelta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<StreamToolCall>? = null,
)

@Serializable
private data class StreamToolCall(
    val index: Int? = null,
    val id: String? = null,
    val function: StreamFunctionCall? = null,
)

@Serializable
private data class StreamFunctionCall(
    val name: String? = null,
    val arguments: String? = null,
)

private class StreamToolAccumulator {
    var id: String = ""
    var name: String = ""
    val arguments: StringBuilder = StringBuilder()
}
