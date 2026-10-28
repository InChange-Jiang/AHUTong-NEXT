package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.RefreshIconButton
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

private enum class CalendarEntryKind { ASSIGNMENT, EVENT }

private data class CalendarEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val start: LocalDate,
    val end: LocalDate,
    val done: Boolean,
    val kind: CalendarEntryKind,
    val startText: String,
    val endText: String,
)

private data class CalendarSegment(
    val entry: CalendarEntry,
    val colStart: Int,
    val colEnd: Int,
    val lane: Int,
)

private data class CalendarWeek(
    val start: LocalDate,
    val cells: List<LocalDate>,
    val segments: List<CalendarSegment>,
    val laneCount: Int,
)

private data class CalendarTone(val background: Color, val foreground: Color)

private val calendarTones = listOf(
    CalendarTone(Color(0xFFE3F0FF), Color(0xFF2F5E8F)),
    CalendarTone(Color(0xFFE6F6EC), Color(0xFF2F6B48)),
    CalendarTone(Color(0xFFFFF1DE), Color(0xFF8A5A1F)),
    CalendarTone(Color(0xFFF3E8FF), Color(0xFF6B3F8F)),
    CalendarTone(Color(0xFFFFE8E8), Color(0xFF8F3F3F)),
    CalendarTone(Color(0xFFE0F7FA), Color(0xFF2F6B73)),
    CalendarTone(Color(0xFFF1F5D8), Color(0xFF5A6420)),
)

private const val SEGMENT_HEIGHT = 18
private const val SEGMENT_STEP = 21

@Composable
fun CalendarEventsPage(
    repository: NextCampusRepository,
    onBack: () -> Unit,
    onMenu: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshRequested by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<CalendarEntry?>(null) }
    var deletingEventId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        loading = true
        withContext(Dispatchers.IO) {
            events = runCatching { repository.calendarEvents() }.getOrDefault(emptyList())
            assignments = runCatching {
                if (refreshRequested) repository.refreshAssignments(90)
                else repository.assignments(90)
            }.getOrElse {
                runCatching { repository.assignments(90) }.getOrDefault(emptyList())
            }
        }
        refreshRequested = false
        loading = false
    }

    val entries = remember(events, assignments) { buildEntries(events, assignments) }
    val weeks = remember(month, entries) { buildWeeks(month, entries) }
    val monthEntries = remember(month, entries) {
        entries.filter { it.start <= month.atEndOfMonth() && it.end >= month.atDay(1) }
            .sortedWith(compareBy<CalendarEntry> { it.start }.thenBy { it.title })
    }

    FeatureScaffold(
        title = "日程",
        onBack = onBack,
        onMenu = onMenu,
        bottomBar = bottomBar,
        actions = {
            RefreshIconButton {
                if (!loading) {
                    refreshRequested = true
                    reload++
                }
            }
        },
    ) {
        AsyncContent(if (loading) LoadState.Loading else LoadState.Ready(Unit)) {
            FeatureScrollColumn {
                PageCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${month.year} 年 ${month.monthValue} 月",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = NextPalette.Ink,
                        )
                        SoftIconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_iconpark_left),
                                contentDescription = "上个月",
                                tint = NextPalette.Ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        SoftIconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_iconpark_left),
                                contentDescription = "下个月",
                                tint = NextPalette.Ink,
                                modifier = Modifier.size(18.dp).rotate(180f),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    weeks.forEach { week ->
                        MonthWeekView(week, month) { selectedEntry = it }
                    }
                    Text(
                        "作业按开放/截止时间自动铺在日历上；已完成作业会淡化。",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }

                PageCard {
                    Text(
                        "本月 ${monthEntries.size} 条",
                        style = MaterialTheme.typography.titleSmall,
                        color = NextPalette.Ink,
                    )
                    if (monthEntries.isEmpty()) {
                        Text(
                            if (assignments.isEmpty()) "还没有缓存作业，点右上角刷新同步。"
                            else "本月没有作业或日程。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NextPalette.InkSecondary,
                        )
                    } else {
                        monthEntries.take(12).forEachIndexed { index, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEntry = entry },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (entry.done) NextPalette.InkSecondary else NextPalette.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        buildString {
                                            append(entry.start.toString())
                                            if (entry.end != entry.start) append(" → ${entry.end}")
                                            if (entry.subtitle.isNotBlank()) {
                                                append(" · ")
                                                append(entry.subtitle)
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NextPalette.InkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    if (entry.kind == CalendarEntryKind.ASSIGNMENT) "作业" else "日程",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.done) NextPalette.InkSecondary else NextPalette.AccentDeep,
                                )
                            }
                            if (index != monthEntries.take(12).lastIndex) {
                                HorizontalDivider(color = NextPalette.Line)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text(entry.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.subtitle.isNotBlank()) {
                        Text(entry.subtitle, color = NextPalette.Ink)
                    }
                    Text(
                        if (entry.start == entry.end) entry.start.toString()
                        else "${entry.start} → ${entry.end}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NextPalette.InkSecondary,
                    )
                    Text(
                        if (entry.kind == CalendarEntryKind.ASSIGNMENT) {
                            if (entry.done) "作业 · 已完成" else "作业 · 未完成"
                        } else {
                            "本地日程"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEntry = null }) { Text("关闭") }
            },
            dismissButton = if (entry.kind == CalendarEntryKind.EVENT) {
                {
                    TextButton(onClick = {
                        selectedEntry = null
                        deletingEventId = entry.id
                    }) { Text("删除") }
                }
            } else null,
        )
    }

    deletingEventId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingEventId = null },
            title = { Text("删除这条日程？") },
            text = { Text("只删除本机保存的日程。") },
            confirmButton = {
                TextButton(onClick = {
                    events = repository.deleteCalendarEvent(id)
                    deletingEventId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingEventId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MonthWeekView(
    week: CalendarWeek,
    month: YearMonth,
    onClick: (CalendarEntry) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            week.cells.forEach { day ->
                val isToday = day == LocalDate.now()
                val outside = day.month != month.month
                Box(
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isToday) NextPalette.AccentSoft else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isToday -> NextPalette.AccentDeep
                                outside -> NextPalette.InkSecondary.copy(alpha = 0.35f)
                                else -> NextPalette.Ink
                            },
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    if (week.segments.isEmpty()) 4.dp
                    else ((week.laneCount * SEGMENT_STEP) + 4).dp,
                ),
        ) {
            val columnWidth = maxWidth / 7
            week.segments.forEach { segment ->
                val tone = toneFor(segment.entry)
                val width = columnWidth * (segment.colEnd - segment.colStart + 1) - 2.dp
                Box(
                    modifier = Modifier
                        .offset(x = columnWidth * segment.colStart, y = (segment.lane * SEGMENT_STEP).dp)
                        .width(width)
                        .height(SEGMENT_HEIGHT.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (segment.entry.done) tone.background.copy(alpha = 0.45f)
                            else tone.background,
                        )
                        .clickable { onClick(segment.entry) }
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        segment.entry.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (segment.entry.done) tone.foreground.copy(alpha = 0.55f) else tone.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun toneFor(entry: CalendarEntry): CalendarTone {
    if (entry.kind == CalendarEntryKind.EVENT) {
        return CalendarTone(NextPalette.AccentSoft, NextPalette.AccentDeep)
    }
    val index = (entry.subtitle.hashCode() and Int.MAX_VALUE) % calendarTones.size
    return calendarTones[index]
}

private fun buildEntries(
    events: List<CalendarEvent>,
    assignments: List<Assignment>,
): List<CalendarEntry> = buildList {
    assignments.forEach { work ->
        val endText = work.endAt ?: work.dueAt
        val startText = work.startAt ?: endText
        val start = parseDay(startText) ?: return@forEach
        val end = parseDay(endText) ?: start
        add(
            CalendarEntry(
                id = "assignment:${work.course}:${work.title}:${work.dueAt}",
                title = work.title,
                subtitle = work.course,
                start = minOf(start, end),
                end = maxOf(start, end),
                done = work.status.contains("已完成"),
                kind = CalendarEntryKind.ASSIGNMENT,
                startText = startText,
                endText = endText,
            ),
        )
    }
    events.forEach { event ->
        val start = parseDay(event.start) ?: return@forEach
        val end = parseDay(event.end) ?: start
        add(
            CalendarEntry(
                id = event.id,
                title = event.title,
                subtitle = "",
                start = minOf(start, end),
                end = maxOf(start, end),
                done = false,
                kind = CalendarEntryKind.EVENT,
                startText = event.start,
                endText = event.end,
            ),
        )
    }
}

private fun parseDay(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

private fun buildWeeks(month: YearMonth, entries: List<CalendarEntry>): List<CalendarWeek> {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val gridStart = first.minusDays(offset.toLong())
    val segments = Array(6) { mutableListOf<CalendarSegment>() }
    entries.forEach { entry ->
        val start = maxOf(entry.start, gridStart)
        val end = minOf(entry.end, gridStart.plusDays(41))
        if (start > end) return@forEach
        var cursor = start
        while (cursor <= end) {
            val weekIndex = ((cursor.toEpochDay() - gridStart.toEpochDay()) / 7).toInt()
            val weekStart = gridStart.plusDays(weekIndex * 7L)
            val weekEnd = weekStart.plusDays(6)
            val segmentEnd = minOf(end, weekEnd)
            segments[weekIndex] += CalendarSegment(
                entry = entry,
                colStart = (cursor.toEpochDay() - weekStart.toEpochDay()).toInt(),
                colEnd = (segmentEnd.toEpochDay() - weekStart.toEpochDay()).toInt(),
                lane = 0,
            )
            cursor = segmentEnd.plusDays(1)
        }
    }
    return (0 until 6).map { weekIndex ->
        val cells = (0..6).map { gridStart.plusDays(weekIndex * 7L + it) }
        val laneEnds = mutableListOf<Int>()
        val placed = segments[weekIndex]
            .sortedWith(compareBy<CalendarSegment> { it.colStart }.thenByDescending { it.colEnd })
            .map { segment ->
                val lane = laneEnds.indexOfFirst { segment.colStart > it }
                if (lane >= 0) {
                    laneEnds[lane] = segment.colEnd
                    segment.copy(lane = lane)
                } else {
                    laneEnds += segment.colEnd
                    segment.copy(lane = laneEnds.size - 1)
                }
            }
        CalendarWeek(
            start = cells.first(),
            cells = cells,
            segments = placed,
            laneCount = maxOf(1, laneEnds.size),
        )
    }
}
