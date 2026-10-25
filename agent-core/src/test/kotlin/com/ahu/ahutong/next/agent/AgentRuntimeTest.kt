package com.ahu.ahutong.next.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class AgentRuntimeTest {

    @Test
    fun readToolResultIsEmitted() = runTest {
        val tool = fakeTool(
            spec = readSpec("get_schedule"),
            output = { ToolOutput(ok = true, data = buildJsonObject { put("count", 1) }) },
        )
        val model = ScriptedModel(
            listOf(
                ModelReply(content = "查一下", toolCalls = listOf(call("get_schedule"))),
                ModelReply(content = "查到了"),
            ),
        )
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(listOf(tool)), clock = { 1L })
                .run(emptyList(), "今天有什么课", ApprovalGate())
                .toList(events)
        }
        advanceUntilIdle()
        job.join()

        val result = events.filterIsInstance<AgentEvent.ToolResultEvent>().single().result
        assertTrue(result.ok)
        assertEquals("get_schedule", result.toolName)
        assertEquals("test-source", result.source)
        assertEquals(1L, result.fetchedAt)
        assertTrue(events.any { it is AgentEvent.AssistantMessage && it.text == "查到了" })
        assertTrue(events.last() is AgentEvent.Done)
    }

    @Test
    fun writeToolWaitsForApprovalBeforeExecuting() = runTest {
        val executed = AtomicBoolean(false)
        val tool = fakeTool(
            spec = writeSpec("add_calendar_event"),
            output = {
                executed.set(true)
                ToolOutput(ok = true, data = buildJsonObject { put("ok", true) })
            },
        )
        val model = ScriptedModel(
            listOf(
                ModelReply(toolCalls = listOf(call("add_calendar_event"))),
                ModelReply(content = "已添加"),
            ),
        )
        val gate = ApprovalGate()
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(listOf(tool)), clock = { 1L })
                .run(emptyList(), "加个日程", gate)
                .toList(events)
        }
        advanceUntilIdle()

        assertFalse(executed.get())
        assertTrue(events.any { it is AgentEvent.ApprovalRequired })

        gate.approve()
        advanceUntilIdle()
        job.join()

        assertTrue(executed.get())
        assertTrue(events.any { it is AgentEvent.ToolResultEvent && it.result.ok })
        assertTrue(events.any { it is AgentEvent.AssistantMessage && it.text == "已添加" })
    }

    @Test
    fun rejectedWriteToolDoesNotExecute() = runTest {
        val executed = AtomicBoolean(false)
        val tool = fakeTool(
            spec = writeSpec("add_calendar_event"),
            output = {
                executed.set(true)
                ToolOutput(ok = true)
            },
        )
        val model = ScriptedModel(
            listOf(
                ModelReply(toolCalls = listOf(call("add_calendar_event"))),
                ModelReply(content = "已取消"),
            ),
        )
        val gate = ApprovalGate()
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(listOf(tool)), clock = { 1L })
                .run(emptyList(), "加个日程", gate)
                .toList(events)
        }
        advanceUntilIdle()

        gate.reject()
        advanceUntilIdle()
        job.join()

        assertFalse(executed.get())
        val result = events.filterIsInstance<AgentEvent.ToolResultEvent>().single().result
        assertFalse(result.ok)
        assertEquals("canceled", result.error?.code)
    }

    @Test
    fun timeoutIsReportedAsToolError() = runTest {
        val tool = fakeTool(
            spec = readSpec("slow_tool").copy(timeoutMs = 10),
            output = {
                delay(100)
                ToolOutput(ok = true)
            },
        )
        val model = ScriptedModel(
            listOf(
                ModelReply(toolCalls = listOf(call("slow_tool"))),
                ModelReply(content = "超时了"),
            ),
        )
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(listOf(tool)), clock = { 1L })
                .run(emptyList(), "慢查询", ApprovalGate())
                .toList(events)
        }
        advanceUntilIdle()
        job.join()

        val result = events.filterIsInstance<AgentEvent.ToolResultEvent>().single().result
        assertFalse(result.ok)
        assertEquals("timeout", result.error?.code)
        assertTrue(result.error?.retryable == true)
    }

    @Test
    fun unknownToolIsReported() = runTest {
        val model = ScriptedModel(
            listOf(
                ModelReply(toolCalls = listOf(call("missing_tool"))),
                ModelReply(content = "工具不存在"),
            ),
        )
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(emptyList()), clock = { 1L })
                .run(emptyList(), "试试", ApprovalGate())
                .toList(events)
        }
        advanceUntilIdle()
        job.join()

        val result = events.filterIsInstance<AgentEvent.ToolResultEvent>().single().result
        assertFalse(result.ok)
        assertEquals("unknown_tool", result.error?.code)
    }

    @Test
    fun streamingDeltasAreForwardedAndFinalized() = runTest {
        val model = object : ChatModel {
            override val name = "streaming"
            override suspend fun complete(messages: List<LlmMessage>, tools: List<ToolSpec>): ModelReply =
                ModelReply("你好")

            override fun stream(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
            ) = flow {
                emit(ModelStreamEvent.Delta("你"))
                emit(ModelStreamEvent.Delta("好"))
                emit(ModelStreamEvent.Completed(ModelReply("你好")))
            }
        }

        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(emptyList()), clock = { 1L })
                .run(emptyList(), "打招呼", ApprovalGate())
                .toList(events)
        }
        advanceUntilIdle()
        job.join()

        assertTrue(events.any { it is AgentEvent.AssistantStart })
        assertEquals(
            listOf("你", "好"),
            events.filterIsInstance<AgentEvent.AssistantDelta>().map { it.text },
        )
        assertEquals("你好", events.filterIsInstance<AgentEvent.AssistantMessage>().single().text)
    }

    @Test
    fun systemPromptIncludesCurrentTimeAndAgentName() = runTest {
        val fixed = Instant.parse("2026-10-25T08:30:00Z").toEpochMilli()
        val model = RecordingModel()
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(emptyList()), clock = { fixed })
                .run(emptyList(), "现在几点", ApprovalGate())
                .toList(events)
        }
        advanceUntilIdle()
        job.join()

        val system = model.messages.first { it.role == "system" }.content.orEmpty()
        assertTrue(system.contains("安筱桐"))
        assertTrue(system.contains("2026-10-25 16:30"))
        assertTrue(system.contains("Asia/Shanghai"))
        assertTrue(system.contains("不要重新自我介绍"))
        assertTrue(system.contains("web_search"))
        assertTrue(system.contains("\\("))
    }

    @Test
    fun rechargeApprovalShowsAmountAndBank() = runTest {
        val tool = fakeTool(spec = writeSpec("recharge_card")) { ToolOutput(ok = true) }
        val model = ScriptedModel(
            listOf(
                ModelReply(
                    toolCalls = listOf(
                        ToolCall(
                            id = "call-1",
                            name = "recharge_card",
                            arguments = buildJsonObject {
                                put("amount", 11)
                                put("bank", "agricultural")
                            },
                        ),
                    ),
                ),
                ModelReply(content = "已充值"),
            ),
        )
        val gate = ApprovalGate()
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(listOf(tool)), clock = { 1L })
                .run(emptyList(), "帮我充11块钱进去，用农行的卡", gate)
                .toList(events)
        }
        advanceUntilIdle()

        val request = events.filterIsInstance<AgentEvent.ApprovalRequired>().single().request
        assertTrue(request.summary.contains("¥11"))
        assertTrue(request.summary.contains("中国农业银行"))
        assertTrue(request.risk.contains("真实扣款"))

        gate.reject()
        advanceUntilIdle()
        job.join()
    }

    @Test
    fun paymentPasswordIsPassedOnlyAfterApproval() = runTest {
        val received = AtomicReference<String?>(null)
        val tool = object : Tool {
            override val spec = writeSpec("recharge_electricity").copy(requiresPaymentPassword = true)
            override suspend fun execute(arguments: JsonObject): ToolOutput = ToolOutput(ok = false)
            override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): ToolOutput {
                received.set(secret)
                return ToolOutput(ok = true)
            }
            override suspend fun describe(arguments: JsonObject): String = "房间：测试房间"
        }
        val model = ScriptedModel(
            listOf(
                ModelReply(toolCalls = listOf(call("recharge_electricity"))),
                ModelReply(content = "已充值"),
            ),
        )
        val gate = ApprovalGate()
        val events = mutableListOf<AgentEvent>()
        val job = launch {
            AgentRuntime(model, ToolRegistry(listOf(tool)), clock = { 1L })
                .run(emptyList(), "给电费充11块", gate)
                .toList(events)
        }
        advanceUntilIdle()

        assertNull(received.get())
        val request = events.filterIsInstance<AgentEvent.ApprovalRequired>().single().request
        assertTrue(request.requiresPaymentPassword)
        assertTrue(request.summary.contains("测试房间"))

        gate.approve("123456")
        advanceUntilIdle()
        job.join()

        assertEquals("123456", received.get())
    }

    private fun call(name: String): ToolCall = ToolCall(
        id = "call-1",
        name = name,
        arguments = buildJsonObject { },
    )

    private fun readSpec(name: String) = ToolSpec(
        name = name,
        displayName = name,
        description = "test",
        parameters = buildJsonObject { },
        kind = ToolKind.READ,
        source = "test-source",
    )

    private fun writeSpec(name: String) = ToolSpec(
        name = name,
        displayName = name,
        description = "test",
        parameters = buildJsonObject { },
        kind = ToolKind.WRITE,
        source = "test-source",
        requiresConfirmation = true,
    )

    private fun fakeTool(
        spec: ToolSpec,
        output: suspend (JsonObject) -> ToolOutput,
    ) = object : Tool {
        override val spec = spec
        override suspend fun execute(arguments: JsonObject): ToolOutput = output(arguments)
    }

    private class ScriptedModel(private val replies: List<ModelReply>) : ChatModel {
        override val name = "scripted"
        private var index = 0

        override suspend fun complete(messages: List<LlmMessage>, tools: List<ToolSpec>): ModelReply {
            val reply = replies[minOf(index, replies.lastIndex)]
            index++
            return reply
        }
    }

    private class RecordingModel : ChatModel {
        override val name = "recording"
        var messages: List<LlmMessage> = emptyList()

        override suspend fun complete(messages: List<LlmMessage>, tools: List<ToolSpec>): ModelReply {
            this.messages = messages
            return ModelReply(content = "现在是 16:30")
        }
    }
}
