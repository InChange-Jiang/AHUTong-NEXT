package com.ahu.ahutong.next.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Serializable
data class WireFunctionCall(
    val name: String,
    val arguments: String = "{}",
)

@Serializable
data class WireToolCall(
    val id: String,
    val type: String = "function",
    val function: WireFunctionCall,
)

@Serializable
data class LlmMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

data class ModelReply(
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
)

sealed interface ModelStreamEvent {
    data class Delta(val text: String) : ModelStreamEvent
    data class Completed(val reply: ModelReply) : ModelStreamEvent
}

interface ChatModel {
    val name: String

    suspend fun complete(messages: List<LlmMessage>, tools: List<ToolSpec>): ModelReply

    /**
     * 默认实现退化为整段返回；支持 SSE 的模型覆盖此方法逐段输出。
     */
    fun stream(messages: List<LlmMessage>, tools: List<ToolSpec>): Flow<ModelStreamEvent> = flow {
        emit(ModelStreamEvent.Completed(complete(messages, tools)))
    }
}

fun ToolCall.toWire(): WireToolCall = WireToolCall(
    id = id,
    function = WireFunctionCall(
        name = name,
        arguments = AgentJson.encodeToString(JsonObject.serializer(), arguments),
    ),
)

fun ToolResult.toToolMessage(): LlmMessage = LlmMessage(
    role = "tool",
    toolCallId = callId,
    name = toolName,
    content = AgentJson.encodeToString(ToolResult.serializer(), this),
)
