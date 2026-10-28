package com.ahu.ahutong.next.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.AssignmentCacheStore
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.LocalNextToast
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftChipStrip
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.SoftTextField
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val assignment: Assignment? = null,
    val event: CalendarEvent? = null,
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
    val context = LocalContext.current
    val accountId = remember {
        LegacyCampusApi.currentStudentId().orEmpty().ifBlank { "anon" }
    }
    val assignmentCache = remember { AssignmentCacheStore(context) }
    var events by remember { mutableStateOf(repository.calendarEvents()) }
    var assignments by remember { mutableStateOf(assignmentCache.load(accountId)) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var initialLoading by remember { mutableStateOf(assignments.isEmpty()) }
    var syncing by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf(assignmentCache.lastModified(accountId)) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshRequested by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<CalendarEntry?>(null) }
    var deletingEventId by remember { mutableStateOf<String?>(null) }
    var showAddEvent by remember { mutableStateOf(false) }
    var webAssignment by remember { mutableStateOf<Assignment?>(null) }
    val showToast = LocalNextToast.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        if (refreshRequested) syncing = true
        withContext(Dispatchers.IO) {
            events = runCatching { repository.calendarEvents() }.getOrDefault(emptyList())
            assignments = runCatching {
                if (refreshRequested) repository.refreshAssignments(90)
                else repository.assignments(90)
            }.getOrElse {
                runCatching { repository.assignments(90) }.getOrDefault(emptyList())
            }
        }
        if (refreshRequested) {
            lastSync = System.currentTimeMillis()
        } else if (lastSync == 0L) {
            lastSync = assignmentCache.lastModified(accountId)
        }
        refreshRequested = false
        syncing = false
        initialLoading = false
    }

    val entries = remember(events, assignments) { buildEntries(events, assignments) }
    val weeks = remember(month, entries) { buildWeeks(month, entries) }
    val monthEntries = remember(month, entries) {
        entries.filter { it.start <= month.atEndOfMonth() && it.end >= month.atDay(1) }
            .sortedWith(compareBy<CalendarEntry> { it.start }.thenBy { it.title })
    }

    val subtitle = when {
        syncing -> "正在同步作业…"
        lastSync > 0 -> "上次同步 ${formatSyncTime(lastSync)}"
        else -> "尚未同步"
    }

    FeatureScaffold(
        title = "日程",
        subtitle = subtitle,
        onBack = onBack,
        onMenu = onMenu,
        bottomBar = bottomBar,
        actions = {
            SoftIconButton(onClick = { showAddEvent = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_iconpark_plus),
                    contentDescription = "新建日程",
                    tint = NextPalette.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            SoftIconButton(onClick = {
                if (!syncing) {
                    refreshRequested = true
                    reload++
                }
            }) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NextPalette.Accent,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconpark_refresh),
                        contentDescription = "同步作业",
                        tint = NextPalette.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        AsyncContent(if (initialLoading) LoadState.Loading else LoadState.Ready(Unit)) {
            FeatureScrollColumn(horizontalPadding = 6.dp) {
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
        val work = entry.assignment
        if (work != null) {
            AssignmentDetailDialog(
                entry = entry,
                work = work,
                onViewQuestions = {
                    selectedEntry = null
                    webAssignment = work
                },
                onOpenChaoxing = {
                    openAssignmentInChaoxing(context, work) { message ->
                        selectedEntry = null
                        showToast(message)
                    }
                },
                onDismiss = { selectedEntry = null },
            )
        } else {
            EventDetailDialog(
                entry = entry,
                onDelete = {
                    selectedEntry = null
                    deletingEventId = entry.id
                },
                onDismiss = { selectedEntry = null },
            )
        }
    }

    webAssignment?.let { work ->
        WorkWebViewDialog(
            title = work.title,
            url = work.detailUrl,
            courseId = work.courseId,
            cookieHeader = Store.getCookie(),
            onDismiss = { webAssignment = null },
        )
    }

    if (showAddEvent) {
        AddCalendarEventDialog(
            onDismiss = { showAddEvent = false },
            onSave = { title, start, end, reminder ->
                showAddEvent = false
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repository.addCalendarEvent(title, start, end, reminder)
                        }
                    }.onSuccess {
                        events = repository.calendarEvents()
                    }.onFailure {
                        showToast(it.message ?: "日程保存失败")
                    }
                }
            },
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
                id = work.workId.ifBlank { "assignment:${work.course}:${work.title}:${work.dueAt}" },
                title = work.title,
                subtitle = work.course,
                start = minOf(start, end),
                end = maxOf(start, end),
                done = work.status.contains("已完成"),
                kind = CalendarEntryKind.ASSIGNMENT,
                startText = startText,
                endText = endText,
                assignment = work,
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
                event = event,
            ),
        )
    }
}

private fun parseDay(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

private fun formatSyncTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),
    )

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

@Composable
private fun AssignmentDetailDialog(
    entry: CalendarEntry,
    work: Assignment,
    onViewQuestions: () -> Unit,
    onOpenChaoxing: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                entry.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.subtitle.isNotBlank()) {
                    Text(
                        entry.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = NextPalette.InkSecondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (entry.done) "已完成" else "未完成",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.done) NextPalette.InkSecondary else NextPalette.AccentDeep,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "作业",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
                HorizontalDivider(color = NextPalette.Line)
                CalendarDetailRow("开始", entry.startText.ifBlank { "未设置" })
                CalendarDetailRow("截止", entry.endText.ifBlank { "未设置" })
                if (work.courseId.isBlank() || work.detailUrl.isBlank()) {
                    Text(
                        "这条作业来自旧缓存。到「作业」页刷新一次，就能看到题目和跳转入口。",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
                if (work.detailUrl.isNotBlank()) {
                    SoftPrimaryButton(
                        text = "查看题目",
                        onClick = onViewQuestions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (work.courseId.isNotBlank()) {
                    SoftSecondaryButton(
                        text = "跳转学习通",
                        onClick = onOpenChaoxing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun EventDetailDialog(
    entry: CalendarEntry,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                entry.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarDetailRow("开始", entry.startText)
                CalendarDetailRow("结束", entry.endText)
                Text(
                    "本机日程，不会写入系统日历。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("删除") }
        },
    )
}

@Composable
private fun CalendarDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.width(46.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NextPalette.InkSecondary,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = NextPalette.Ink,
        )
    }
}

@Composable
private fun AddCalendarEventDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, start: String, end: String, reminder: Int) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    val defaultStart = remember {
        LocalDateTime.now().withSecond(0).withNano(0).plusHours(1).withMinute(0)
    }
    var title by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf(defaultStart.format(formatter)) }
    var endText by remember { mutableStateOf(defaultStart.plusHours(1).format(formatter)) }
    var reminder by remember { mutableIntStateOf(15) }
    val start = remember(startText) { parseCalendarTime(startText, formatter) }
    val end = remember(endText) { parseCalendarTime(endText, formatter) }
    val valid = title.isNotBlank() && start != null && end != null && end > start

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建日程") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SoftTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    placeholder = "例如：交实验报告",
                    modifier = Modifier.fillMaxWidth(),
                )
                SoftTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = "开始时间",
                    placeholder = "yyyy-MM-dd HH:mm",
                    modifier = Modifier.fillMaxWidth(),
                )
                SoftTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = "结束时间",
                    placeholder = "yyyy-MM-dd HH:mm",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "提前提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                SoftChipStrip {
                    listOf(0 to "不提醒", 15 to "15分", 30 to "30分", 60 to "60分")
                        .forEach { (minutes, label) ->
                            SoftChip(
                                label,
                                onClick = { reminder = minutes },
                                selected = reminder == minutes,
                            )
                        }
                }
                if (startText.isNotBlank() && start == null) {
                    Text(
                        "开始时间格式应为 yyyy-MM-dd HH:mm",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.Danger,
                    )
                } else if (start != null && end != null && end <= start) {
                    Text(
                        "结束时间要晚于开始时间",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.Danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) {
                        onSave(
                            title.trim(),
                            start!!.format(formatter),
                            end!!.format(formatter),
                            reminder,
                        )
                    }
                },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun parseCalendarTime(text: String, formatter: DateTimeFormatter): LocalDateTime? =
    runCatching { LocalDateTime.parse(text.trim(), formatter) }.getOrNull()

/**
 * 跳学习通 App 打开该课程作业列表。普通课与镜像课都走票据端点，
 * classId 从本地课程缓存现查；缺少班级信息时只提示，不降级到浏览器。
 */
private fun openAssignmentInChaoxing(
    context: Context,
    work: Assignment,
    onMessage: (String) -> Unit,
) {
    val clazzId = Store.getCourses()
        .firstOrNull { it.courseId == work.courseId }
        ?.clazzId
    if (clazzId.isNullOrBlank()) {
        onMessage("缺少课程班级信息，请先在课程页同步一次")
        return
    }
    val ticketUrl = "https://mobilelearn.chaoxing.com/task/getStuWorkAndExamSkipUrl" +
        "?courseId=${work.courseId}&classId=$clazzId#INNER"
    val link = runCatching {
        "chaoxingshareback://xuexitong.com/?sharebacktype=1" +
            "&title=" + URLEncoder.encode(work.course, "UTF-8") +
            "&url=" + URLEncoder.encode(ticketUrl, "UTF-8")
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
        setPackage("com.chaoxing.mobile")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        onMessage("未安装学习通 App")
    }
}
