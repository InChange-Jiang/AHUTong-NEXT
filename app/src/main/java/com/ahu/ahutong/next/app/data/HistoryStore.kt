package com.ahu.ahutong.next.app.data

import android.content.Context
import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.ToolResult
import com.ahu.ahutong.next.ui.chat.ChatItem
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val messageCount: Int,
)

/**
 * 多会话本地历史。旧版单会话 chat_history.json 会在首次读取时迁入。
 * 所有读写都在同一把锁里，避免流式保存与切换会话互相覆盖。
 */
class HistoryStore(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val sessionsFile = File(dir, "chat_sessions.json")
    private val legacyFile = File(dir, "chat_history.json")
    private val lock = Any()

    fun load(): List<ChatItem> = synchronized(lock) {
        val data = read()
        val current = data.sessions.firstOrNull { it.id == data.currentId }
            ?: data.sessions.maxByOrNull { it.updatedAt }
        current?.items?.toChatItems().orEmpty()
    }

    fun currentId(): String? = synchronized(lock) {
        val data = read()
        data.currentId.takeIf { id -> data.sessions.any { it.id == id } }
            ?: data.sessions.maxByOrNull { it.updatedAt }?.id
    }

    fun save(items: List<ChatItem>) = synchronized(lock) {
        val data = read()
        val currentId = data.currentId.takeIf { id -> data.sessions.any { it.id == id } }
            ?: data.sessions.maxByOrNull { it.updatedAt }?.id
        val stored = items.toStoredItems()
        val next = if (currentId == null) {
            val session = StoredSession(
                id = UUID.randomUUID().toString(),
                title = titleOf(stored),
                updatedAt = System.currentTimeMillis(),
                items = stored,
            )
            data.copy(currentId = session.id, sessions = data.sessions + session)
        } else {
            data.copy(
                sessions = data.sessions.map { session ->
                    if (session.id == currentId) {
                        session.copy(
                            title = titleOf(stored),
                            updatedAt = System.currentTimeMillis(),
                            items = stored,
                        )
                    } else {
                        session
                    }
                },
            )
        }
        write(next)
    }

    fun startNewSession(): String = synchronized(lock) {
        val data = read()
        val current = data.sessions.firstOrNull { it.id == data.currentId }
        if (current != null && current.items.isEmpty()) return@synchronized current.id
        val session = StoredSession(
            id = UUID.randomUUID().toString(),
            title = "新对话",
            updatedAt = System.currentTimeMillis(),
            items = emptyList(),
        )
        write(data.copy(currentId = session.id, sessions = data.sessions + session))
        session.id
    }

    fun switchTo(id: String): List<ChatItem> = synchronized(lock) {
        val data = read()
        val session = data.sessions.firstOrNull { it.id == id } ?: return@synchronized load()
        write(data.copy(currentId = id))
        session.items.toChatItems()
    }

    fun delete(id: String) = synchronized(lock) {
        val data = read()
        val remaining = data.sessions.filterNot { it.id == id }
        val nextCurrent = when {
            data.currentId != id -> data.currentId
            else -> remaining.maxByOrNull { it.updatedAt }?.id.orEmpty()
        }
        write(data.copy(currentId = nextCurrent, sessions = remaining))
    }

    fun clearAll() = synchronized(lock) {
        runCatching { sessionsFile.delete() }
    }

    fun summaries(): List<ChatSessionSummary> = synchronized(lock) {
        read().sessions
            .sortedByDescending { it.updatedAt }
            .map { session ->
                ChatSessionSummary(
                    id = session.id,
                    title = session.title.ifBlank { "新对话" },
                    preview = previewOf(session.items),
                    updatedAt = session.updatedAt,
                    messageCount = session.items.count { it.type == TYPE_USER || it.type == TYPE_ASSISTANT },
                )
            }
    }

    fun count(): Int = load().size

    private fun read(): SessionsFile {
        if (sessionsFile.exists()) {
            return runCatching {
                AgentJson.decodeFromString(SessionsFile.serializer(), sessionsFile.readText())
            }.getOrElse { SessionsFile() }
        }
        if (legacyFile.exists()) {
            val stored = runCatching {
                AgentJson.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(StoredItem.serializer()),
                    legacyFile.readText(),
                )
            }.getOrDefault(emptyList())
            if (stored.isNotEmpty()) {
                val session = StoredSession(
                    id = UUID.randomUUID().toString(),
                    title = titleOf(stored),
                    updatedAt = System.currentTimeMillis(),
                    items = stored,
                )
                val migrated = SessionsFile(currentId = session.id, sessions = listOf(session))
                write(migrated)
                runCatching { legacyFile.renameTo(File(dir, "chat_history.migrated.json")) }
                return migrated
            }
        }
        return SessionsFile()
    }

    private fun write(data: SessionsFile) {
        runCatching {
            sessionsFile.writeText(AgentJson.encodeToString(SessionsFile.serializer(), data))
        }
    }

    private fun titleOf(items: List<StoredItem>): String {
        val first = items.firstOrNull { it.type == TYPE_USER && !it.text.isNullOrBlank() }
            ?.text
            ?.trim()
            .orEmpty()
        return first.take(24).ifBlank { "新对话" }
    }

    private fun previewOf(items: List<StoredItem>): String {
        val last = items.lastOrNull {
            (it.type == TYPE_USER || it.type == TYPE_ASSISTANT) && !it.text.isNullOrBlank()
        }?.text?.replace('\n', ' ')?.trim().orEmpty()
        return last.take(60)
    }

    private fun List<StoredItem>.toChatItems(): List<ChatItem> = mapNotNull { item ->
        when (item.type) {
            TYPE_USER -> ChatItem.User(item.id, item.text.orEmpty())
            TYPE_ASSISTANT -> ChatItem.Assistant(item.id, item.text.orEmpty())
            TYPE_TOOL -> item.tool?.let { ChatItem.Tool(item.id, it) }
            TYPE_ERROR -> ChatItem.Error(item.id, item.text.orEmpty())
            TYPE_NOTICE -> ChatItem.Notice(item.id, item.text.orEmpty())
            else -> null
        }
    }

    private fun List<ChatItem>.toStoredItems(): List<StoredItem> = mapNotNull { item ->
        when (item) {
            is ChatItem.User -> StoredItem(TYPE_USER, item.id, text = item.text)
            is ChatItem.Assistant -> StoredItem(TYPE_ASSISTANT, item.id, text = item.text)
            is ChatItem.Tool -> StoredItem(TYPE_TOOL, item.id, tool = item.result)
            is ChatItem.Error -> StoredItem(TYPE_ERROR, item.id, text = item.text)
            is ChatItem.Notice -> StoredItem(TYPE_NOTICE, item.id, text = item.text)
            is ChatItem.Approval -> null
        }
    }

    private companion object {
        const val TYPE_USER = "user"
        const val TYPE_ASSISTANT = "assistant"
        const val TYPE_TOOL = "tool"
        const val TYPE_ERROR = "error"
        const val TYPE_NOTICE = "notice"
    }
}

@Serializable
private data class SessionsFile(
    val currentId: String = "",
    val sessions: List<StoredSession> = emptyList(),
)

@Serializable
private data class StoredSession(
    val id: String,
    val title: String = "新对话",
    val updatedAt: Long = 0L,
    val items: List<StoredItem> = emptyList(),
)

@Serializable
private data class StoredItem(
    val type: String,
    val id: String,
    val text: String? = null,
    val tool: ToolResult? = null,
)
