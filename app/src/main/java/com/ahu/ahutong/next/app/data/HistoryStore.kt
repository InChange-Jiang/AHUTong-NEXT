package com.ahu.ahutong.next.app.data

import android.content.Context
import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.ToolResult
import com.ahu.ahutong.next.ui.chat.ChatItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 单会话本地历史。首版用 JSON 文件，后续换 Room 时保持读写接口不变。
 */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "chat_history.json")

    fun load(): List<ChatItem> {
        if (!file.exists()) return emptyList()
        val stored = runCatching {
            AgentJson.decodeFromString(
                ListSerializer(StoredItem.serializer()),
                file.readText(),
            )
        }.getOrDefault(emptyList())

        return stored.mapNotNull { item ->
            when (item.type) {
                TYPE_USER -> ChatItem.User(item.id, item.text.orEmpty())
                TYPE_ASSISTANT -> ChatItem.Assistant(item.id, item.text.orEmpty())
                TYPE_TOOL -> item.tool?.let { ChatItem.Tool(item.id, it) }
                TYPE_ERROR -> ChatItem.Error(item.id, item.text.orEmpty())
                else -> null
            }
        }
    }

    fun save(items: List<ChatItem>) {
        val stored = items.mapNotNull { item ->
            when (item) {
                is ChatItem.User -> StoredItem(TYPE_USER, item.id, text = item.text)
                is ChatItem.Assistant -> StoredItem(TYPE_ASSISTANT, item.id, text = item.text)
                is ChatItem.Tool -> StoredItem(TYPE_TOOL, item.id, tool = item.result)
                is ChatItem.Error -> StoredItem(TYPE_ERROR, item.id, text = item.text)
                is ChatItem.Notice -> StoredItem(TYPE_NOTICE, item.id, text = item.text)
                is ChatItem.Approval -> null
            }
        }
        runCatching {
            file.writeText(
                AgentJson.encodeToString(ListSerializer(StoredItem.serializer()), stored),
            )
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    fun count(): Int = load().size

    private companion object {
        const val TYPE_USER = "user"
        const val TYPE_ASSISTANT = "assistant"
        const val TYPE_TOOL = "tool"
        const val TYPE_ERROR = "error"
        const val TYPE_NOTICE = "notice"
    }
}

@Serializable
private data class StoredItem(
    val type: String,
    val id: String,
    val text: String? = null,
    val tool: ToolResult? = null,
)
