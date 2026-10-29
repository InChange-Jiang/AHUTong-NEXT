package com.ahu.ahutong.next.app.data

import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WriteJournalStoreTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "write-journal-test-${System.nanoTime()}")
    private val args: JsonObject = buildJsonObject {
        put("amount", 11)
        put("bank", "agricultural")
    }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun journal(): WriteJournalStore {
        dir.mkdirs()
        return WriteJournalStore(File(dir, "journal.json")) { "test-user" }
    }

    @Test
    fun pendingWriteBlocksDuplicate() = runBlocking<Unit> {
        val journal = journal()
        assertNull(journal.check("recharge_card", args))
        journal.begin("recharge_card", args)
        assertNotNull(journal.check("recharge_card", args))
        assertNull(journal.check("recharge_card", buildJsonObject { put("amount", 12) }))
    }

    @Test
    fun ambiguousResultStaysBlocked() = runBlocking<Unit> {
        val journal = journal()
        journal.begin("recharge_card", args)
        journal.finish(
            "recharge_card",
            args,
            ToolResult(
                callId = "1",
                toolName = "recharge_card",
                ok = false,
                error = ToolError("write_timeout_unknown", "结果未知", retryable = false),
                source = "一卡通系统",
                fetchedAt = 1L,
            ),
        )
        assertNotNull(journal.check("recharge_card", args))
    }

    @Test
    fun successAndDefinitiveFailureReleaseTheGuard() = runBlocking<Unit> {
        val journal = journal()
        journal.begin("recharge_card", args)
        journal.finish(
            "recharge_card",
            args,
            ToolResult(
                callId = "1",
                toolName = "recharge_card",
                ok = true,
                source = "一卡通系统",
                fetchedAt = 1L,
            ),
        )
        assertNull(journal.check("recharge_card", args))

        journal.begin("recharge_card", args)
        journal.finish(
            "recharge_card",
            args,
            ToolResult(
                callId = "2",
                toolName = "recharge_card",
                ok = false,
                error = ToolError("recharge_failed", "余额不足", retryable = false),
                source = "一卡通系统",
                fetchedAt = 1L,
            ),
        )
        assertNull(journal.check("recharge_card", args))
    }

    @Test
    fun scopesAreIsolated() = runBlocking<Unit> {
        val journal = journal()
        journal.begin("recharge_card", args)
        val otherUser = WriteJournalStore(File(dir, "journal.json")) { "other-user" }
        assertNull(otherUser.check("recharge_card", args))
    }
}
