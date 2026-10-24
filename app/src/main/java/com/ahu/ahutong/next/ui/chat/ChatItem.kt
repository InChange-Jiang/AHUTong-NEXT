package com.ahu.ahutong.next.ui.chat

import com.ahu.ahutong.next.agent.ApprovalRequest
import com.ahu.ahutong.next.agent.ToolResult

enum class ApprovalDecision {
    PENDING,
    APPROVED,
    REJECTED,
}

sealed interface ChatItem {
    val id: String

    data class User(override val id: String, val text: String) : ChatItem

    data class Assistant(override val id: String, val text: String) : ChatItem

    data class Tool(override val id: String, val result: ToolResult) : ChatItem

    data class Approval(
        override val id: String,
        val request: ApprovalRequest,
        val decision: ApprovalDecision = ApprovalDecision.PENDING,
    ) : ChatItem

    data class Error(override val id: String, val text: String) : ChatItem

    data class Notice(override val id: String, val text: String) : ChatItem
}

data class ChatUiState(
    val items: List<ChatItem> = emptyList(),
    val input: String = "",
    val isRunning: Boolean = false,
    val status: String? = null,
    val pendingApprovalId: String? = null,
    val hasApiKey: Boolean = false,
    val modelLabel: String = "本地演示",
)

data class ToolLabResult(
    val toolName: String,
    val ok: Boolean,
    val latencyMs: Long,
    val payload: String,
    val error: String? = null,
)
