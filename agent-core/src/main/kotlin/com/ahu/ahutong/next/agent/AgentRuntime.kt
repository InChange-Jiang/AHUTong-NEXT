package com.ahu.ahutong.next.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
            add(LlmMessage(role = "system", content = systemPromptWithTime()))
            // 首版只回放对话文本；工具卡不进入上下文，避免把旧结果当成新事实。
            addAll(history.takeLast(24))
            add(LlmMessage(role = "user", content = userText))
        }.toMutableList()

        emit(AgentEvent.Status("正在理解你的问题…"))

        try {
            var step = 0
            while (step < maxSteps) {
                step++
                val collected = collectModelStream(
                    model = model,
                    messages = messages,
                    tools = registry.specs,
                    onStart = { emit(AgentEvent.AssistantStart) },
                    onDelta = { emit(AgentEvent.AssistantDelta(it)) },
                )
                val reply = collected.reply
                val visibleText = collected.text.trim()

                if (reply.toolCalls.isEmpty()) {
                    emit(AgentEvent.AssistantMessage(visibleText.ifBlank { "我没有拿到可用结果。" }))
                    emit(AgentEvent.Done)
                    return@flow
                }

                if (visibleText.isNotEmpty()) {
                    emit(AgentEvent.AssistantMessage(visibleText))
                }

                messages += LlmMessage(
                    role = "assistant",
                    content = reply.content ?: visibleText.takeIf { it.isNotBlank() },
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
                            summary = approvalSummary(tool.spec, call.arguments),
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

    private suspend fun collectModelStream(
        model: ChatModel,
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        onStart: suspend () -> Unit,
        onDelta: suspend (String) -> Unit,
    ): CollectedReply {
        val text = StringBuilder()
        var started = false
        var completed: ModelReply? = null

        model.stream(messages, tools).collect { event ->
            when (event) {
                is ModelStreamEvent.Delta -> {
                    if (!started) {
                        started = true
                        onStart()
                    }
                    text.append(event.text)
                    onDelta(event.text)
                }

                is ModelStreamEvent.Completed -> completed = event.reply
            }
        }

        val reply = completed ?: throw IllegalStateException("模型流没有返回完成事件")
        val finalText = reply.content?.takeIf { it.isNotBlank() } ?: text.toString()
        return CollectedReply(reply = reply, text = finalText)
    }

    private data class CollectedReply(
        val reply: ModelReply,
        val text: String,
    )

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

    private fun approvalSummary(spec: ToolSpec, arguments: JsonObject): String = when (spec.name) {
        "add_calendar_event" -> "将在本地日历中新建一条日程。"
        "recharge_card" -> {
            val amount = arguments["amount"]?.jsonPrimitive?.content ?: "未知"
            val bank = when (arguments["bank"]?.jsonPrimitive?.content) {
                "agricultural" -> "中国农业银行"
                "cmb" -> "招商银行"
                else -> "指定银行卡"
            }
            "将从$bank 扣款 ¥$amount，充值到你的校园卡。"
        }
        else -> "将执行「${spec.displayName}」，这个操作会产生实际变更。"
    }

    private fun systemPromptWithTime(): String {
        val now = Instant.ofEpochMilli(clock()).atZone(ZONE)
        return buildString {
            append(systemPrompt.trim())
            append("\n\n当前时间：")
            append(now.format(TIME_FORMAT))
            append("（")
            append(ZONE.id)
            append("）。用户说“今天 / 明天 / 本周 / 下周”时，以这个时间为准。")
        }
    }

    private fun riskText(spec: ToolSpec): String = when {
        spec.name == "recharge_card" ->
            "资金操作：确认后会立即从银行卡发起真实扣款，请核对金额和银行卡。"
        spec.kind == ToolKind.WRITE -> "写入操作：确认后才会执行，执行前不会产生任何变更。"
        else -> "只读操作"
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE", Locale.CHINA)

        const val DEFAULT_SYSTEM_PROMPT = """
你的名字叫安筱桐，是安大通 NEXT 的校园助手。你可以调用工具查询学生的课表、作业、成绩、余额、账单、电费，或在用户确认后添加日程、给校园卡充值。

规则：
1. 涉及个人数据时必须调用工具，不要凭空编造课程、作业、成绩或日期。
2. 信息不足时先追问，不要猜。
3. 写操作必须等用户在确认卡中同意后才算执行，不要提前声称完成。
4. 回答简洁，使用中文；需要展示明细时交给结果卡片，不要重复罗列。
5. 工具返回结果后，直接基于结果回答用户；不要重新自我介绍，也不要忽略工具结果。
6. 需要实时信息（新闻、政策、赛事、天气、校外资料等）时调用 web_search；引用搜索结果时说明来源。
7. 数学公式必须使用 \( ... \) 行内、\[ ... \] 块级；不要用 $ 作为公式分隔符，避免与货币符号冲突。
8. 充值只有在用户本轮明确说出金额和银行卡时才能调用；金额或银行卡不清楚就先追问，不要猜，也不要替用户选。
9. 充值会真实扣款。失败后不要自动重试，先让用户核对余额和账单，确认没到账再征得同意重新发起。
"""
    }
}
