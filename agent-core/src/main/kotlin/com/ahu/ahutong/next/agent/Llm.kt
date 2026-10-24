package com.ahu.ahutong.next.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

interface ChatModel {
    val name: String
    suspend fun complete(messages: List<LlmMessage>, tools: List<ToolSpec>): ModelReply
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
