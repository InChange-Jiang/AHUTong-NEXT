package com.ahu.ahutong.next.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.ToolResult
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.data.CourseOccurrence
import com.ahu.ahutong.next.data.Grade
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val prettyJson = Json { prettyPrint = true }

@Composable
fun ToolResultCard(result: ToolResult) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    toolTitle(result.toolName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (result.ok) "已完成" else "失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result.ok) colors.secondary else colors.error,
                )
            }

            if (!result.ok) {
                Text(
                    result.error?.message ?: "执行失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            } else {
                when (result.toolName) {
                    "get_schedule" -> ScheduleBody(result.data)
                    "get_assignments" -> AssignmentBody(result.data)
                    "get_grades" -> GradeBody(result.data)
                    "add_calendar_event" -> CalendarBody(result.data)
                    else -> Text(
                        prettyJson.encodeToString(JsonObject.serializer(), result.data ?: JsonObject(emptyMap())),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            HorizontalDivider(color = colors.outline.copy(alpha = 0.45f))
            Text(
                "来源：${result.source} · 更新于 ${formatTimestamp(result.fetchedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleBody(data: JsonObject?) {
    val courses = decodeCourses(data)
    if (courses.isEmpty()) {
        EmptyHint("这段时间没有课程。")
        return
    }

    val grouped = courses.groupBy { it.date }
    grouped.entries.take(3).forEach { (date, list) ->
        Text(
            formatDate(date),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        list.take(3).forEach { CourseRow(it) }
    }
    if (grouped.size > 3) {
        Text(
            "还有 ${grouped.size - 3} 天未展示",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CourseRow(course: CourseOccurrence) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.width(52.dp)) {
            Text(course.startTime, style = MaterialTheme.typography.labelMedium)
            Text(
                course.endTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                course.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${course.location} · ${course.teacher}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssignmentBody(data: JsonObject?) {
    val assignments = decodeAssignments(data)
    if (assignments.isEmpty()) {
        EmptyHint("最近没有作业。")
        return
    }

    assignments.take(6).forEach { assignment ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    assignment.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${assignment.course} · 截止 ${assignment.dueAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                assignment.status,
                style = MaterialTheme.typography.labelSmall,
                color = if (assignment.status == "已完成") {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun GradeBody(data: JsonObject?) {
    val grades = decodeGrades(data)
    if (grades.isEmpty()) {
        EmptyHint("没有查到成绩。")
        return
    }

    val gpa = data?.get("gpaAverage")?.jsonPrimitive?.content.orEmpty()
    val credits = data?.get("credits")?.jsonPrimitive?.content.orEmpty()
    Row(verticalAlignment = Alignment.Bottom) {
        Text("加权绩点", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            gpa,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$credits 学分",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    grades.forEach { grade ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                grade.course,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${grade.credit.toInt()} 学分",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                formatScore(grade.score),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "绩点 ${grade.gpa}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarBody(data: JsonObject?) {
    val event = decodeEvent(data)
    if (event == null) {
        EmptyHint("没有拿到日程详情。")
        return
    }

    Text(
        event.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        "${event.start} - ${event.end}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "提前 ${event.reminderMinutes} 分钟提醒",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ApprovalCard(
    item: ChatItem.Approval,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pending = item.decision == ApprovalDecision.PENDING
    val statusText = when (item.decision) {
        ApprovalDecision.PENDING -> "等待中"
        ApprovalDecision.APPROVED -> "已确认"
        ApprovalDecision.REJECTED -> "已取消"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (pending) colors.primary.copy(alpha = 0.55f) else colors.outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "需要你确认",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pending) colors.primary else colors.onSurfaceVariant,
                )
            }

            Text(item.request.summary, style = MaterialTheme.typography.bodyMedium)

            item.request.arguments.forEach { (key, value) ->
                if (key != "reminder_minutes") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            labelFor(key),
                            modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            value.display(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            val reminder = item.request.arguments["reminder_minutes"]?.display()
            if (reminder != null) {
                Text(
                    "提前 $reminder 分钟提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }

            Text(
                item.request.risk,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))

            if (pending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onReject) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApprove) { Text("确认执行") }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun toolTitle(name: String): String = when (name) {
    "get_schedule" -> "课表"
    "get_assignments" -> "作业"
    "get_grades" -> "成绩"
    "add_calendar_event" -> "新建日程"
    else -> name
}

private fun labelFor(key: String): String = when (key) {
    "title" -> "标题"
    "start" -> "开始"
    "end" -> "结束"
    else -> key
}

private fun JsonElement.display(): String =
    runCatching { jsonPrimitive.content }.getOrElse { toString() }

private fun formatDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
}.getOrDefault(iso)

private fun formatTimestamp(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}.getOrDefault("")

private fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()

private fun decodeCourses(data: JsonObject?): List<CourseOccurrence> {
    val element = data?.get("courses") ?: return emptyList()
    return runCatching {
        AgentJson.decodeFromJsonElement(
            ListSerializer(CourseOccurrence.serializer()),
            element,
        )
    }.getOrDefault(emptyList())
}

private fun decodeAssignments(data: JsonObject?): List<Assignment> {
    val element = data?.get("assignments") ?: return emptyList()
    return runCatching {
        AgentJson.decodeFromJsonElement(
            ListSerializer(Assignment.serializer()),
            element,
        )
    }.getOrDefault(emptyList())
}

private fun decodeGrades(data: JsonObject?): List<Grade> {
    val element = data?.get("grades") ?: return emptyList()
    return runCatching {
        AgentJson.decodeFromJsonElement(
            ListSerializer(Grade.serializer()),
            element,
        )
    }.getOrDefault(emptyList())
}

private fun decodeEvent(data: JsonObject?): CalendarEvent? {
    val element = data?.get("event") ?: return null
    return runCatching {
        AgentJson.decodeFromJsonElement(CalendarEvent.serializer(), element)
    }.getOrNull()
}
