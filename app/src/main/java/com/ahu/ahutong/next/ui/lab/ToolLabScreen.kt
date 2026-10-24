package com.ahu.ahutong.next.ui.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolRegistry
import com.ahu.ahutong.next.agent.ToolSpec
import com.ahu.ahutong.next.data.MockCampusRepository
import com.ahu.ahutong.next.tools.campusTools
import com.ahu.ahutong.next.ui.chat.ToolLabResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolLabScreen(onBack: () -> Unit) {
    val repository = remember { MockCampusRepository() }
    val registry = remember { ToolRegistry(campusTools(repository)) }
    val specs = registry.specs

    var selected by remember { mutableStateOf(specs.first()) }
    var args by rememberSaveable(selected.name) { mutableStateOf(defaultArgs(selected.name)) }
    var result by remember { mutableStateOf<ToolLabResult?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("工具工作台", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(specs, key = { it.name }) { spec ->
                    FilterChip(
                        selected = selected.name == spec.name,
                        onClick = {
                            selected = spec
                            args = defaultArgs(spec.name)
                            result = null
                        },
                        label = { Text(spec.displayName) },
                    )
                }
            }

            Text(
                selected.description,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.kind.name == "WRITE") "写入" else "只读",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected.kind.name == "WRITE") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
                Text(
                    "  ·  来源：${selected.source}  ·  超时 ${selected.timeoutMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "参数 schema",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                pretty(selected.parameters),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = args,
                onValueChange = { args = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("参数 JSON") },
                minLines = 4,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            Button(
                onClick = {
                    if (running) return@Button
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
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 10.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (running) "执行中…" else "运行工具")
            }

            if (selected.kind.name == "WRITE") {
                Text(
                    "工作台会直接执行写入工具，仅用于调试；正式对话里会先弹确认卡。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            result?.let { r ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (r.ok) "成功" else "失败",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (r.ok) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            Text(
                                "  ${r.latencyMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        r.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            r.payload,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
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
