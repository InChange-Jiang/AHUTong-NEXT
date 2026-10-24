package com.ahu.ahutong.next.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * 单 agent + 工具循环。模型在云端，工具在本机执行，写操作在 [ApprovalGate] 处暂停。
 */
class AgentRuntime(
    private val model: ChatModel,
    private val registry: ToolRegistry,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val maxSteps: Int = 6,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun run(
        history: List<LlmMessage>,
        userText: String,
        gate: ApprovalGate,
    ): Flow<AgentEvent> = flow {
        val messages = buildList {
            add(LlmMessage(role = "system", content = systemPrompt))
            // 首版只回放对话文本；工具卡不进入上下文，避免把旧结果当成新事实。
            addAll(history.takeLast(24))
            add(LlmMessage(role = "user", content = userText))
        }.toMutableList()

        emit(AgentEvent.Status("正在理解你的问题…"))

        try {
            var step = 0
            while (step < maxSteps) {
                step++
                val reply = model.complete(messages, registry.specs)

                if (reply.toolCalls.isEmpty()) {
                    val text = reply.content?.trim().orEmpty()
                    emit(AgentEvent.AssistantMessage(text.ifBlank { "我没有拿到可用结果。" }))
                    emit(AgentEvent.Done)
                    return@flow
                }

                messages += LlmMessage(
                    role = "assistant",
                    content = reply.content,
                    toolCalls = reply.toolCalls.map { it.toWire() },
                )

                for (call in reply.toolCalls) {
                    val tool = registry.get(call.name)
                    if (tool == null) {
                        val result = ToolResult(
                            callId = call.id,
                            toolName = call.name,
                            ok = false,
                            error = ToolError("unknown_tool", "未知工具：${call.name}"),
                            source = "agent",
                            fetchedAt = clock(),
                        )
                        emit(AgentEvent.ToolResultEvent(result))
                        messages += result.toToolMessage()
                        continue
                    }

                    if (tool.spec.requiresConfirmation) {
                        val request = ApprovalRequest(
                            id = UUID.randomUUID().toString(),
                            toolName = tool.spec.name,
                            displayName = tool.spec.displayName,
                            summary = approvalSummary(tool.spec),
                            arguments = call.arguments,
                            risk = riskText(tool.spec),
                        )
                        val decision = gate.prepare()
                        emit(AgentEvent.ApprovalRequired(request))

                        val approved = try {
                            decision.await()
                        } catch (e: CancellationException) {
                            throw e
                        }

                        if (!approved) {
                            val result = ToolResult(
                                callId = call.id,
                                toolName = call.name,
                                ok = false,
                                error = ToolError("canceled", "用户取消了操作"),
                                source = tool.spec.source,
                                fetchedAt = clock(),
                            )
                            emit(AgentEvent.ToolResultEvent(result))
                            messages += result.toToolMessage()
                            continue
                        }
                    }

                    emit(AgentEvent.Status("正在执行：${tool.spec.displayName}…"))
                    val result = execute(tool, call)
                    emit(AgentEvent.ToolResultEvent(result))
                    messages += result.toToolMessage()
                }
            }

            emit(AgentEvent.AssistantMessage("这个任务步骤有点多，我先停在这里。你可以把需求拆小一点再试。"))
            emit(AgentEvent.Done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AgentEvent.Failed(e.message?.takeIf { it.isNotBlank() } ?: "执行失败，请稍后再试"))
        }
    }

    private suspend fun execute(tool: Tool, call: ToolCall): ToolResult {
        return try {
            val output = withTimeout(tool.spec.timeoutMs) { tool.execute(call.arguments) }
            ToolResult(
                callId = call.id,
                toolName = tool.spec.name,
                ok = output.ok,
                data = output.data,
                error = output.error,
                source = tool.spec.source,
                fetchedAt = clock(),
                cacheAgeMs = output.cacheAgeMs,
            )
        } catch (e: TimeoutCancellationException) {
            ToolResult(
                callId = call.id,
                toolName = tool.spec.name,
                ok = false,
                error = ToolError("timeout", "查询超时，请稍后重试", retryable = true),
                source = tool.spec.source,
                fetchedAt = clock(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult(
                callId = call.id,
                toolName = tool.spec.name,
                ok = false,
                error = ToolError("tool_failed", e.message ?: "工具执行失败", retryable = true),
                source = tool.spec.source,
                fetchedAt = clock(),
            )
        }
    }

    private fun approvalSummary(spec: ToolSpec): String = when (spec.name) {
        "add_calendar_event" -> "将在本地日历中新建一条日程。"
        else -> "将执行「${spec.displayName}」，这个操作会产生实际变更。"
    }

    private fun riskText(spec: ToolSpec): String = when (spec.kind) {
        ToolKind.WRITE -> "写入操作：确认后才会执行，执行前不会产生任何变更。"
        ToolKind.READ -> "只读操作"
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """
你是安大通 NEXT 的校园助手。你可以调用工具查询学生的课表、作业、成绩，或在用户确认后添加日程。

规则：
1. 涉及个人数据时必须调用工具，不要凭空编造课程、作业、成绩或日期。
2. 信息不足时先追问，不要猜。
3. 写操作必须等用户在确认卡中同意后才算执行，不要提前声称完成。
4. 回答简洁，使用中文；需要展示明细时交给结果卡片，不要重复罗列。
"""
    }
}
