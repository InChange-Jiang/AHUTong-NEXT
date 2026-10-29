package com.ahu.ahutong.next.app.data

import android.content.Context
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.ToolResult
import com.ahu.ahutong.next.agent.WriteJournal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

/**
 * 写操作幂等日志。只保存“工具名 + 参数摘要”，不保存参数原文、金额明细或支付密码。
 *
 * 目的：充值/缴费超时后，结果可能已经成功。模型若重试同一笔操作，这里会先拦下，
 * 让用户去查余额/账单，而不是直接再扣一次。
 */
class WriteJournalStore internal constructor(
    private val file: File,
    private val scope: () -> String,
) : WriteJournal {
    constructor(context: Context) : this(
        file = File(context.applicationContext.filesDir, "write_journal.json"),
        scope = {
            SessionStore.currentUser()?.xh?.takeIf(String::isNotBlank) ?: "anonymous"
        },
    )

    private val lock = Any()

    override suspend fun check(toolName: String, arguments: JsonObject): String? =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val key = keyOf(toolName, arguments)
                val now = System.currentTimeMillis()
                val record = read().firstOrNull { it.key == key } ?: return@synchronized null
                val age = now - record.updatedAt
                when {
                    record.status == STATUS_PENDING && age < PENDING_GRACE_MS ->
                        "上一次相同的写操作正在提交，请稍后确认结果，不要重复提交"

                    (record.status == STATUS_PENDING || record.status == STATUS_UNKNOWN) &&
                        age < UNKNOWN_RETENTION_MS ->
                        "上一次相同的写操作结果未知，请先查询余额/账单确认，不要重复提交"

                    else -> {
                        remove(key)
                        null
                    }
                }
            }
        }

    override suspend fun begin(toolName: String, arguments: JsonObject) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = keyOf(toolName, arguments)
            val records = read().filterNot { it.key == key } +
                JournalRecord(key, toolName, STATUS_PENDING, System.currentTimeMillis())
            write(records)
        }
    }

    override suspend fun finish(
        toolName: String,
        arguments: JsonObject,
        result: ToolResult,
    ) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = keyOf(toolName, arguments)
            val ambiguous = result.error?.let { error ->
                error.retryable || error.code in AMBIGUOUS_CODES
            } ?: false
            val records = read().filterNot { it.key == key }.toMutableList()
            if (!result.ok && ambiguous) {
                records += JournalRecord(key, toolName, STATUS_UNKNOWN, System.currentTimeMillis())
            }
            write(records)
        }
    }

    private fun read(): List<JournalRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            AgentJson.decodeFromString(JournalFile.serializer(), file.readText()).records
        }.getOrDefault(emptyList())
    }

    private fun write(records: List<JournalRecord>) {
        runCatching {
            file.writeText(AgentJson.encodeToString(JournalFile.serializer(), JournalFile(records)))
        }
    }

    private fun remove(key: String) {
        write(read().filterNot { it.key == key })
    }

    private fun keyOf(toolName: String, arguments: JsonObject): String {
        val sorted = buildJsonObject {
            arguments.entries.sortedBy { it.key }.forEach { (name, value) -> put(name, value) }
        }
        val raw = "${scope()}|$toolName|$sorted"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    @Serializable
    private data class JournalFile(val records: List<JournalRecord> = emptyList())

    @Serializable
    private data class JournalRecord(
        val key: String,
        val toolName: String,
        val status: String,
        val updatedAt: Long,
    )

    private companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_UNKNOWN = "UNKNOWN"
        const val PENDING_GRACE_MS = 2 * 60 * 1000L
        const val UNKNOWN_RETENTION_MS = 30 * 60 * 1000L
        val AMBIGUOUS_CODES = setOf(
            "timeout",
            "tool_failed",
            "write_timeout_unknown",
            "write_result_unknown",
        )
    }
}
