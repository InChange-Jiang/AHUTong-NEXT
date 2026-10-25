package com.ahu.ahutong.next.ui.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolRegistry
import com.ahu.ahutong.next.agent.ToolSpec
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.tools.GetAttendanceTool
import com.ahu.ahutong.next.tools.GetFreeRoomsTool
import com.ahu.ahutong.next.tools.GetLostFoundTool
import com.ahu.ahutong.next.tools.GetNoticesTool
import com.ahu.ahutong.next.tools.GetProgramCompletionTool
import com.ahu.ahutong.next.tools.GetWeatherTool
import com.ahu.ahutong.next.tools.campusTools
import com.ahu.ahutong.next.ui.chat.ToolLabResult
import com.ahu.ahutong.next.ui.components.SoftBackground
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSurface
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ToolLabScreen(onBack: () -> Unit) {
    val repository = remember { NextCampusRepository() }
    val registry = remember {
        ToolRegistry(
            // 工作台绕过确认卡和支付密码，不能放真实扣款的工具进来。
            campusTools(repository).filterNot {
                it.spec.requiresPaymentPassword || it.spec.name == "recharge_card"
            } + listOf(
                GetWeatherTool(),
                GetAttendanceTool(),
                GetProgramCompletionTool(),
                GetLostFoundTool(),
                GetNoticesTool(),
                GetFreeRoomsTool(),
            ),
        )
    }
    val specs = registry.specs

    var selected by remember { mutableStateOf(specs.first()) }
    var args by rememberSaveable(selected.name) { mutableStateOf(defaultArgs(selected.name)) }
    var result by remember { mutableStateOf<ToolLabResult?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SoftBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SoftIconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconpark_left),
                        contentDescription = "返回",
                        tint = NextPalette.Ink,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "工具工作台",
                    style = MaterialTheme.typography.titleLarge,
                    color = NextPalette.Ink,
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(specs, key = { it.name }) { spec ->
                    SoftChip(
                        label = spec.displayName,
                        selected = selected.name == spec.name,
                        onClick = {
                            selected = spec
                            args = defaultArgs(spec.name)
                            result = null
                        },
                    )
                }
            }

            SoftSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        selected.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NextPalette.Ink,
                    )
                    Text(
                        "${if (selected.kind.name == "WRITE") "写入" else "只读"} · ${selected.source} · 超时 ${selected.timeoutMs / 1000}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected.kind.name == "WRITE") {
                            NextPalette.Danger
                        } else {
                            NextPalette.InkSecondary
                        },
                    )
                    Text(
                        "参数 schema",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NextPalette.Ink,
                    )
                    SoftSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = NextPalette.SurfaceSoft,
                        elevation = 0.dp,
                    ) {
                        Text(
                            pretty(selected.parameters),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
            }

            SoftSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "参数 JSON",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NextPalette.Ink,
                    )
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = NextPalette.Ink,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NextPalette.SurfaceSoft,
                            unfocusedContainerColor = NextPalette.SurfaceSoft,
                            focusedBorderColor = NextPalette.Accent.copy(alpha = 0.55f),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = NextPalette.Accent,
                            focusedTextColor = NextPalette.Ink,
                            unfocusedTextColor = NextPalette.Ink,
                        ),
                    )
                    SoftPrimaryButton(
                        text = if (running) "执行中…" else "运行工具",
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (running) return@SoftPrimaryButton
                            running = true
                            result = null
                            scope.launch {
                                val started = System.currentTimeMillis()
                                val output = execute(registry, selected, args)
                                result = ToolLabResult(
                                    toolName = selected.name,
                                    ok = output.ok,
                                    latencyMs = System.currentTimeMillis() - started,
                                    payload = AgentJson.encodeToString(ToolOutput.serializer(), output),
                                    error = output.error?.message,
                                )
                                running = false
                            }
                        },
                    )
                    if (selected.kind.name == "WRITE") {
                        Text(
                            "工作台会直接执行写入工具，仅用于调试；正式对话里会先弹确认卡。",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
            }

            result?.let { r ->
                SoftSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (r.ok) "成功" else "失败",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (r.ok) NextPalette.Success else NextPalette.Danger,
                            )
                            Text(
                                "  ${r.latencyMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                        }
                        r.error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = NextPalette.Danger,
                            )
                        }
                        SoftSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = NextPalette.SurfaceSoft,
                            elevation = 0.dp,
                        ) {
                            Text(
                                r.payload,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = NextPalette.InkSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

private suspend fun execute(
    registry: ToolRegistry,
    spec: ToolSpec,
    args: String,
): ToolOutput {
    val parsed = runCatching {
        AgentJson.parseToJsonElement(args).jsonObject
    }.getOrElse {
        return ToolOutput(
            ok = false,
            error = ToolError("invalid_json", "参数不是合法的 JSON 对象"),
        )
    }

    val tool = registry.get(spec.name)
        ?: return ToolOutput(ok = false, error = ToolError("unknown_tool", "未找到工具"))

    return withContext(Dispatchers.IO) {
        try {
            withTimeout(spec.timeoutMs) { tool.execute(parsed) }
        } catch (e: TimeoutCancellationException) {
            ToolOutput(ok = false, error = ToolError("timeout", "执行超时", retryable = true))
        } catch (e: Exception) {
            ToolOutput(ok = false, error = ToolError("tool_failed", e.message ?: "执行失败", retryable = true))
        }
    }
}

private fun defaultArgs(name: String): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)
    return when (name) {
        "get_schedule" -> """{"days": 7}"""
        "get_assignments" -> """{"days": 14}"""
        "get_grades" -> "{}"
        "add_calendar_event" -> """
            {
              "title": "小组会议",
              "start": "${start.format(formatter)}",
              "end": "${start.plusHours(1).format(formatter)}",
              "reminder_minutes": 15
            }
        """.trimIndent()
        else -> "{}"
    }
}

private fun pretty(element: JsonElement): String =
    runCatching {
        kotlinx.serialization.json.Json { prettyPrint = true }
            .encodeToString(JsonElement.serializer(), element)
    }.getOrDefault(element.toString())
