package com.ahu.ahutong.next.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApprovalRequest(
    val id: String,
    val toolName: String,
    val displayName: String,
    val summary: String,
    val arguments: JsonObject,
    val risk: String,
    val requiresPaymentPassword: Boolean = false,
    /** 本轮已经读过外部内容时，提醒用户不要被网页/邮件/通知里的文字带着走。 */
    val untrustedWarning: String? = null,
)

sealed interface AgentEvent {
    data class Status(val text: String) : AgentEvent
    data object AssistantStart : AgentEvent
    data class AssistantDelta(val text: String) : AgentEvent
    data class AssistantMessage(val text: String) : AgentEvent
    data class ToolResultEvent(val result: ToolResult) : AgentEvent
    data class ApprovalRequired(val request: ApprovalRequest) : AgentEvent
    data class Failed(val message: String) : AgentEvent
    data object Done : AgentEvent
}
