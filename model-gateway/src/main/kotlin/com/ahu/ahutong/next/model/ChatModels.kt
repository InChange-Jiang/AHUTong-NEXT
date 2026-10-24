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

/**
 * 没有 API Key 时的本地演示模型：用关键词路由到同一批工具，
 * 让整条 UI / 确认 / 卡片链路在离线状态下也能跑通。
 */
class FakeChatModel : ChatModel {
    override val name: String = "本地演示"

    override suspend fun complete(messages: List<LlmMessage>, tools: List<ToolSpec>): ModelReply {
        val last = messages.lastOrNull()
        if (last?.role == "tool") {
            val result = runCatching {
                AgentJson.decodeFromString(ToolResult.serializer(), last.content.orEmpty())
            }.getOrNull()
            return ModelReply(summarize(result))
        }

        val text = messages.lastOrNull { it.role == "user" }?.content.orEmpty().trim()
        return when {
            text.contains("作业") || text.contains("没交") || text.contains("截止") ->
                ModelReply("我查一下最近的作业。", listOf(call("get_assignments", """{"days":14}""")))

            text.contains("课表") || text.contains("上课") || text.contains("几节课") ->
                ModelReply("我查一下你最近的课表。", listOf(call("get_schedule", """{"days":7}""")))

            text.contains("成绩") || text.contains("绩点") || text.contains("分数") ->
                ModelReply("我查一下你的成绩。", listOf(call("get_grades", "{}")))

            text.contains("天气") || text.contains("下雨") || text.contains("气温") ->
                ModelReply("我查一下合肥的天气。", listOf(call("get_weather", """{"city":"合肥"}""")))

            text.contains("考勤") || text.contains("签到") || text.contains("旷课") ->
                ModelReply("我查一下最近的考勤记录。", listOf(call("get_attendance", """{"page":1}""")))

            text.contains("培养方案") || text.contains("毕业学分") || text.contains("完成度") ->
                ModelReply("我查一下培养方案的完成情况。", listOf(call("get_program_completion", "{}")))

            text.contains("余额") || text.contains("一卡通") ->
                ModelReply("我查一下你的一卡通余额。", listOf(call("get_balance", "{}")))

            text.contains("账单") || text.contains("流水") || text.contains("消费") ->
                ModelReply("我查一下近期的一卡通流水。", listOf(call("get_bills", "{}")))

            text.contains("考试") ->
                ModelReply("我查一下考试安排。", listOf(call("get_exams", "{}")))

            text.contains("电费") || text.contains("电量") ->
                ModelReply("我查一下宿舍电费。", listOf(call("get_electricity", "{}")))

            text.contains("日程") || text.contains("提醒") || text.contains("加到") ->
                ModelReply(
                    "我整理了一条日程，需要你确认后才会写入。",
                    listOf(call("add_calendar_event", calendarArgs(text))),
                )

            else -> ModelReply(
                "我可以帮你查课表、作业、成绩、考试、考勤、培养方案、天气、一卡通余额与账单、宿舍电费，也可以在确认后添加日程。你可以直接问“这周还有哪些作业”。",
            )
        }
    }

    override fun stream(messages: List<LlmMessage>, tools: List<ToolSpec>): Flow<ModelStreamEvent> = flow {
        val reply = complete(messages, tools)
        val text = reply.content.orEmpty()
        if (text.isNotEmpty()) {
            text.chunked(10).forEach { chunk ->
                emit(ModelStreamEvent.Delta(chunk))
                delay(18)
            }
        }
        emit(ModelStreamEvent.Completed(reply))
    }

    private fun summarize(result: ToolResult?): String {
        if (result == null) return "工具返回了无法解析的结果。"
        if (!result.ok) {
            return if (result.error?.code == "canceled") {
                "好的，这次操作已取消。"
            } else {
                "这次没成功：${result.error?.message ?: "未知错误"}"
            }
        }
        return when (result.toolName) {
            "get_schedule" -> "课表查到了，明细在下面的卡片里。"
            "get_assignments" -> "作业查到了，按截止时间排在下面。"
            "get_grades" -> "成绩查到了，明细在下面。"
            "get_balance" -> "余额查到了，在下面的卡片里。"
            "get_bills" -> "账单查到了，在下面的卡片里。"
            "get_exams" -> "考试安排查到了，在下面的卡片里。"
            "get_electricity" -> "电费查到了，在下面的卡片里。"
            "get_weather" -> "天气查到了，在下面。"
            "get_attendance" -> "考勤查到了，在下面。"
            "get_program_completion" -> "培养方案完成情况在下面。"
            "add_calendar_event" -> "日程已添加，可在本地历史里查看。"
            else -> "结果在下面。"
        }
    }

    private fun call(name: String, args: String): ToolCall = ToolCall(
        id = UUID.randomUUID().toString(),
        name = name,
        arguments = runCatching { AgentJson.parseToJsonElement(args).jsonObject }
            .getOrElse { buildJsonObject { } },
    )

    private fun calendarArgs(text: String) = runCatching {
        val start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        buildJsonObject {
            put("title", calendarTitle(text))
            put("start", start.format(formatter))
            put("end", start.plusHours(1).format(formatter))
            put("reminder_minutes", 15)
        }.toString()
    }.getOrDefault("{}")

    private fun calendarTitle(text: String): String {
        val cleaned = text
            .replace("帮我", "")
            .replace("请", "")
            .replace("把", "")
            .replace("添加到日程", "")
            .replace("加到日程", "")
            .replace("加进日程", "")
            .replace("日程", "")
            .replace("提醒我", "")
            .trim()
        return cleaned.take(30).ifBlank { "校园日程" }
    }
}

class OpenAiCompatibleChatModel(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) : ChatModel {

    override val name: String = "DeepSeek · $model"

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
