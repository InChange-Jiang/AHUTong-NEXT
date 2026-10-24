package com.ahu.ahutong.next.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

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
}
