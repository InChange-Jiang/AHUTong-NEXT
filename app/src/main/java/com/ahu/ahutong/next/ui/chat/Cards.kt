package com.ahu.ahutong.next.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.ToolResult
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.data.CourseOccurrence
import com.ahu.ahutong.next.data.Grade
import com.ahu.ahutong.next.ui.components.AgentAvatar
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.SoftSurface
import com.ahu.ahutong.next.ui.theme.NextPalette
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
    SoftSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NextPalette.AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(toolIcon(result.toolName)),
                        contentDescription = null,
                        tint = NextPalette.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        toolTitle(result.toolName),
                        style = MaterialTheme.typography.titleSmall,
                        color = NextPalette.Ink,
                    )
                    Text(
                        if (result.ok) "已完成" else "执行失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (result.ok) NextPalette.Success else NextPalette.Danger,
                    )
                }
            }

            if (!result.ok) {
                Text(
                    result.error?.message ?: "执行失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NextPalette.Danger,
                )
            } else {
                when (result.toolName) {
                    "get_schedule" -> ScheduleBody(result.data)
                    "get_assignments" -> AssignmentBody(result.data)
                    "get_grades" -> GradeBody(result.data)
                    "add_calendar_event" -> CalendarBody(result.data)
                    else -> Text(
                        prettyJson.encodeToString(JsonElement.serializer(), result.data ?: JsonObject(emptyMap())),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            HorizontalDivider(color = NextPalette.Line)
            Text(
                "来源：${result.source} · 更新于 ${formatTimestamp(result.fetchedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
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
            color = NextPalette.Accent,
        )
        list.take(3).forEach { CourseRow(it) }
    }
    if (grouped.size > 3) {
        Text(
            "还有 ${grouped.size - 3} 天未展示",
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
        )
    }
}

@Composable
private fun CourseRow(course: CourseOccurrence) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.width(54.dp)) {
            Text(
                course.startTime,
                style = MaterialTheme.typography.labelMedium,
                color = NextPalette.Ink,
            )
            Text(
                course.endTime,
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                course.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = NextPalette.Ink,
            )
            Text(
                "${course.location} · ${course.teacher}",
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
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
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (assignment.status == "已完成") NextPalette.Success else NextPalette.Danger,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    assignment.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = NextPalette.Ink,
                )
                Text(
                    "${assignment.course} · 截止 ${assignment.dueAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
            Text(
                assignment.status,
                style = MaterialTheme.typography.labelSmall,
                color = if (assignment.status == "已完成") {
                    NextPalette.InkSecondary
                } else {
                    NextPalette.Danger
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
        Text("加权绩点", style = MaterialTheme.typography.labelMedium, color = NextPalette.InkSecondary)
        Spacer(Modifier.width(10.dp))
        Text(
            gpa,
            style = MaterialTheme.typography.headlineMedium,
            color = NextPalette.AccentDeep,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$credits 学分",
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
        )
    }

    grades.forEach { grade ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                grade.course,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = NextPalette.Ink,
            )
            Text(
                "${grade.credit.toInt()} 学分",
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                formatScore(grade.score),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = NextPalette.Ink,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "绩点 ${grade.gpa}",
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
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
        color = NextPalette.Ink,
    )
    Text(
        "${event.start} - ${event.end}",
        style = MaterialTheme.typography.bodyMedium,
        color = NextPalette.Ink,
    )
    Text(
        "提前 ${event.reminderMinutes} 分钟提醒",
        style = MaterialTheme.typography.labelSmall,
        color = NextPalette.InkSecondary,
    )
}

@Composable
fun ApprovalCard(
    item: ChatItem.Approval,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val pending = item.decision == ApprovalDecision.PENDING
    val statusText = when (item.decision) {
        ApprovalDecision.PENDING -> "等待确认"
        ApprovalDecision.APPROVED -> "已确认"
        ApprovalDecision.REJECTED -> "已取消"
    }

    SoftSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (pending) androidx.compose.ui.graphics.Color(0xFFF3F7FF) else NextPalette.Surface,
        elevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgentAvatar(size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "需要你确认",
                        style = MaterialTheme.typography.titleSmall,
                        color = NextPalette.Ink,
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pending) NextPalette.Accent else NextPalette.InkSecondary,
                    )
                }
            }

            Text(
                item.request.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = NextPalette.Ink,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.request.arguments.forEach { (key, value) ->
                    if (key != "reminder_minutes") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                labelFor(key),
                                modifier = Modifier.width(64.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                            Text(
                                value.display(),
                                style = MaterialTheme.typography.bodySmall,
                                color = NextPalette.Ink,
                            )
                        }
                    }
                }
                val reminder = item.request.arguments["reminder_minutes"]?.display()
                if (reminder != null) {
                    Text(
                        "提前 $reminder 分钟提醒",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            }

            Text(
                item.request.risk,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NextPalette.SurfaceSoft)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )

            if (pending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoftSecondaryButton(text = "取消", onClick = onReject)
                    Spacer(Modifier.width(10.dp))
                    SoftPrimaryButton(text = "确认执行", onClick = onApprove)
                }
            }
        }
    }
}

@Composable
fun ErrorCard(text: String) {
    SoftSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = NextPalette.DangerSoft,
        elevation = 4.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = NextPalette.Danger,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = NextPalette.InkSecondary,
    )
}

private fun toolIcon(name: String): Int = when (name) {
    "get_schedule" -> R.drawable.ic_iconpark_calendar
    "get_assignments" -> R.drawable.ic_iconpark_assignment
    "get_grades" -> R.drawable.ic_iconpark_grades
    "add_calendar_event" -> R.drawable.ic_iconpark_plus
    else -> R.drawable.ic_iconpark_grades
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
