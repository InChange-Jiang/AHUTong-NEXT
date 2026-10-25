package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.ProgramCompletionGateway
import com.ahu.ahutong.data.attendance.AttendanceRecord
import com.ahu.ahutong.data.attendance.KqcardAttendanceRepository
import com.ahu.ahutong.data.crawler.model.jwxt.ProgramCompletion
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.CourseOccurrence
import com.ahu.ahutong.next.data.Exam
import com.ahu.ahutong.next.data.Grade
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.EmptyState
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SchedulePage(repository: NextCampusRepository, onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<List<CourseOccurrence>>>(LoadState.Loading) }
    var weekView by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.schedule(14) } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }

    FeatureScaffold(
        title = "课表",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoftChip("周视图", onClick = { weekView = true }, selected = weekView)
            SoftChip("列表", onClick = { weekView = false }, selected = !weekView)
        }
        AsyncContent(state, onRetry = { reload++ }) {
            val courses = (state as LoadState.Ready).value
            if (courses.isEmpty()) {
                EmptyState("这段时间没有课程。")
            } else if (weekView) {
                WeekTimetable(courses)
            } else {
                val grouped = courses.groupBy { it.date }
                FeatureScrollColumn {
                    grouped.forEach { (date, list) ->
                        PageCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    LocalDate.parse(date).format(
                                        DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (date == LocalDate.now().toString()) {
                                        NextPalette.AccentDeep
                                    } else {
                                        NextPalette.Ink
                                    },
                                )
                                Text(
                                    "${list.size} 节",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NextPalette.InkSecondary,
                                )
                            }
                            list.sortedBy { it.startTime }.forEach { course -> CourseRow(course) }
                        }
                    }
                }
            }
        }
    }
}

private val coursePalette = listOf(
    androidx.compose.ui.graphics.Color(0xFF5B8DEF),
    androidx.compose.ui.graphics.Color(0xFF62B58F),
    androidx.compose.ui.graphics.Color(0xFFE5A64B),
    androidx.compose.ui.graphics.Color(0xFFB07ACC),
    androidx.compose.ui.graphics.Color(0xFFE07A7A),
    androidx.compose.ui.graphics.Color(0xFF4FA9C9),
    androidx.compose.ui.graphics.Color(0xFF9BAE4C),
)

@Composable
private fun WeekTimetable(courses: List<CourseOccurrence>) {
    val days = (0..6).map { LocalDate.now().plusDays(it.toLong()) }
    val byDate = courses.groupBy { it.date }
    val horizontal = rememberScrollState()
    val hourHeight = 64.dp
    val dayWidth = 104.dp
    val totalHeight = hourHeight * 14

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Spacer(Modifier.width(40.dp))
            Row(Modifier.horizontalScroll(horizontal)) {
                days.forEach { day ->
                    val isToday = day == LocalDate.now()
                    Column(
                        modifier = Modifier.width(dayWidth).padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            weekdayLabel(day),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) NextPalette.AccentDeep else NextPalette.InkSecondary,
                        )
                        Text(
                            "${day.monthValue}/${day.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) NextPalette.AccentDeep else NextPalette.InkSecondary,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Box(Modifier.width(40.dp).height(totalHeight)) {
                (8..21).forEach { hour ->
                    Text(
                        "%02d:00".format(hour),
                        modifier = Modifier.offset(y = hourHeight * (hour - 8)),
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            }
            Row(Modifier.horizontalScroll(horizontal)) {
                days.forEach { day ->
                    Box(Modifier.width(dayWidth).height(totalHeight)) {
                        byDate[day.toString()].orEmpty().forEach { course ->
                            CourseBlock(course, hourHeight)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun CourseBlock(course: CourseOccurrence, hourHeight: androidx.compose.ui.unit.Dp) {
    val start = runCatching { java.time.LocalTime.parse(course.startTime.padStart(5, '0')) }.getOrNull() ?: return
    val end = runCatching { java.time.LocalTime.parse(course.endTime.padStart(5, '0')) }.getOrNull() ?: return
    val startMinutes = (start.hour - 8) * 60 + start.minute
    val endMinutes = (end.hour - 8) * 60 + end.minute
    val top = (startMinutes.coerceIn(0, 14 * 60) / 60f) * hourHeight.value
    val height = ((endMinutes - startMinutes).coerceAtLeast(30) / 60f) * hourHeight.value
    val color = coursePalette[(course.name.hashCode() and Int.MAX_VALUE) % coursePalette.size]
    Column(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .offset(y = top.dp)
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            course.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (course.location.isNotBlank()) {
            Text(
                course.location,
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private fun weekdayLabel(day: LocalDate): String = when (day.dayOfWeek.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

@Composable
private fun CourseRow(course: CourseOccurrence) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(58.dp)) {
            Text(
                course.startTime,
                style = MaterialTheme.typography.labelMedium,
                color = NextPalette.AccentDeep,
            )
            Text(
                course.endTime,
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(course.name, style = MaterialTheme.typography.bodyMedium, color = NextPalette.Ink)
            Text(
                listOfNotNull(
                    course.location.takeIf { it.isNotBlank() },
                    course.teacher.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )
        }
    }
}

@Composable
fun GradesPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<List<Grade>>>(LoadState.Loading) }
    var rank by remember { mutableStateOf<GpaRankInfo?>(null) }
    var selectedTerm by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.grades(null) } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
        rank = runCatching { withContext(Dispatchers.IO) { LegacyCampusApi.gpaRank().requireValue() } }
            .getOrNull()
    }

    FeatureScaffold(
        title = "成绩",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val all = (state as LoadState.Ready).value
            val terms = all.map { it.term }.distinct()
            val grades = if (selectedTerm == null) all else all.filter { it.term == selectedTerm }
            val credits = grades.sumOf { it.credit }
            val weighted = if (credits <= 0.0) 0.0 else grades.sumOf { it.gpa * it.credit } / credits
            FeatureScrollColumn {
                if (terms.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SoftChip("全部", onClick = { selectedTerm = null }, selected = selectedTerm == null)
                        terms.forEach { term ->
                            SoftChip(term, onClick = { selectedTerm = term }, selected = selectedTerm == term)
                        }
                    }
                }

                PageCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("加权绩点", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            Text(
                                String.format(Locale.CHINA, "%.2f", weighted),
                                style = MaterialTheme.typography.headlineMedium,
                                color = NextPalette.AccentDeep,
                            )
                        }
                        Text(
                            "${credits.compact()} 学分 · ${grades.size} 门",
                            style = MaterialTheme.typography.labelMedium,
                            color = NextPalette.InkSecondary,
                        )
                    }
                    rank?.let {
                        HorizontalDivider(color = NextPalette.Line)
                        LabelValueRow(
                            "专业排名",
                            if (it.majorHeadCount > 0) "${it.majorRank} / ${it.majorHeadCount}" else "暂无",
                        )
                        LabelValueRow("累计绩点", String.format(Locale.CHINA, "%.2f", it.gpa))
                        LabelValueRow("总学分", it.totalCredits.compact())
                    }
                }

                if (grades.isEmpty()) {
                    EmptyState("没有查到成绩。")
                } else {
                    PageCard {
                        grades.forEachIndexed { index, grade ->
                            GradeRow(grade)
                            if (index != grades.lastIndex) HorizontalDivider(color = NextPalette.Line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeRow(grade: Grade) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(grade.course, style = MaterialTheme.typography.bodyMedium, color = NextPalette.Ink)
            Text(
                "${grade.credit.compact()} 学分 · 绩点 ${String.format(Locale.CHINA, "%.1f", grade.gpa)}",
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )
        }
        Text(
            grade.scoreText?.takeIf { it.isNotBlank() } ?: grade.score.compact(),
            style = MaterialTheme.typography.titleMedium,
            color = if (grade.score >= 60) NextPalette.AccentDeep else NextPalette.Danger,
        )
    }
}

@Composable
fun ExamsPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<List<Exam>>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.exams() } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "考试",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val exams = (state as LoadState.Ready).value
            if (exams.isEmpty()) {
                EmptyState("暂无考试安排。")
            } else {
                val today = LocalDate.now().toString()
                val upcoming = exams.filter { examDate(it.time) == null || examDate(it.time)!! >= today }
                val finished = exams.filter { examDate(it.time) != null && examDate(it.time)!! < today }
                FeatureScrollColumn {
                    if (upcoming.isNotEmpty()) {
                        Text("待考", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                        upcoming.forEach { ExamCard(it) }
                    }
                    if (finished.isNotEmpty()) {
                        Text("已结束", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                        finished.forEach { ExamCard(it, past = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamCard(exam: Exam, past: Boolean = false) {
    PageCard {
        Text(
            exam.course,
            style = MaterialTheme.typography.titleSmall,
            color = if (past) NextPalette.InkSecondary else NextPalette.Ink,
        )
        LabelValueRow("时间", exam.time, if (past) NextPalette.InkSecondary else NextPalette.AccentDeep)
        LabelValueRow("地点", exam.location.ifBlank { "待定" })
        LabelValueRow("座位", exam.seat.ifBlank { "待定" })
    }
}

private fun examDate(time: String): String? =
    Regex("\\d{4}-\\d{2}-\\d{2}").find(time)?.value

@Composable
fun AssignmentsPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var days by remember { mutableIntStateOf(14) }
    var state by remember { mutableStateOf<LoadState<List<Assignment>>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload, days) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.assignments(days) } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "作业",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoftChip("14 天", onClick = { days = 14 }, selected = days == 14)
            SoftChip("30 天", onClick = { days = 30 }, selected = days == 30)
        }
        AsyncContent(state, onRetry = { reload++ }) {
            val works = (state as LoadState.Ready).value
            if (works.isEmpty()) EmptyState("这段时间没有作业。") else FeatureScrollColumn {
                works.forEach { work ->
                    PageCard {
                        Text(work.title, style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                        Text(work.course, style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "截止 ${work.dueAt}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = NextPalette.AccentDeep,
                            )
                            Text(
                                work.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (work.status.contains("已完成")) NextPalette.Success else NextPalette.Warning,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendancePage(onBack: () -> Unit) {
    var page by remember { mutableIntStateOf(1) }
    var records by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(target: Int, append: Boolean) {
        scope.launch {
            if (append) loadingMore = true else state = LoadState.Loading
            val result = runCatching {
                withContext(Dispatchers.IO) { KqcardAttendanceRepository.loadPage(status = 0, page = target) }
            }
            result.fold(
                onSuccess = {
                    total = it.totalCount
                    records = if (append) records + it.records else it.records
                    page = target
                    state = LoadState.Ready(Unit)
                },
                onFailure = { state = it.toLoadFailure() },
            )
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) { load(1, false) }

    FeatureScaffold(
        title = "考勤",
        onBack = onBack,
        actions = { TextButton(onClick = { load(1, false) }) { Text("刷新") } },
    ) {
        AsyncContent(state, onRetry = { load(1, false) }) {
            if (records.isEmpty()) EmptyState("暂无考勤记录。") else FeatureScrollColumn {
                Text(
                    "共 $total 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                records.forEach { record ->
                    PageCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                record.courseName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                color = NextPalette.Ink,
                            )
                            Text(
                                record.status.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (record.status.label) {
                                    "正常" -> NextPalette.Success
                                    "迟到" -> NextPalette.Warning
                                    else -> NextPalette.Danger
                                },
                            )
                        }
                        Text(
                            "${record.classDate} ${record.startTime}-${record.endTime} · ${if (record.room.contains(record.building)) record.room else record.building + record.room}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                        Text(
                            "打卡 ${record.punchTime.ifBlank { "—" }} · ${record.teacher}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
                if (records.size < total) {
                    TextButton(
                        enabled = !loadingMore,
                        onClick = { load(page + 1, true) },
                    ) {
                        Text(if (loadingMore) "加载中…" else "加载更多")
                    }
                }
            }
        }
    }
}

@Composable
fun CompletionPage(onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<ProgramCompletion>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching {
            withContext(Dispatchers.IO) { ProgramCompletionGateway.refresh().requireValue().first }
        }.fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "培养方案",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val program = (state as LoadState.Ready).value
            FeatureScrollColumn {
                PageCard {
                    Text(program.programName, style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${(program.passedCredits / program.requiredCredits.coerceAtLeast(1.0) * 100).toInt()}%",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineMedium,
                            color = NextPalette.AccentDeep,
                        )
                        Text(
                            "${program.passedCredits.compact()} / ${program.requiredCredits.compact()} 学分",
                            style = MaterialTheme.typography.labelMedium,
                            color = NextPalette.InkSecondary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (program.passedCredits / program.requiredCredits.coerceAtLeast(1.0)).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = NextPalette.Accent,
                        trackColor = NextPalette.AccentSoft,
                    )
                    Text(
                        if (program.auditPublished) "审核结果已发布" else "审核结果尚未发布，以教务为准",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
                program.modules.forEach { module ->
                    PageCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                module.nameZh,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                color = NextPalette.Ink,
                            )
                            Text(
                                "${module.passedCredits.compact()}/${module.requiredCredits.compact()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NextPalette.AccentDeep,
                            )
                        }
                        val unpassed = module.courses.filter { it.status != "PASSED" }.take(6)
                        unpassed.forEach { course ->
                            Row {
                                Text(
                                    course.nameZh,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NextPalette.InkSecondary,
                                )
                                Text(
                                    courseStatus(course.status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (course.status) {
                                        "PASSED" -> NextPalette.Success
                                        "TAKING" -> NextPalette.AccentDeep
                                        else -> NextPalette.Warning
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FreeRoomsPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var campus by remember { mutableIntStateOf(1) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var state by remember { mutableStateOf<LoadState<List<String>>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload, campus, date) {
        state = LoadState.Loading
        state = runCatching {
            withContext(Dispatchers.IO) {
                LegacyCampusApi.freeRooms(campus, date, (1..13).map(Int::toString)).requireValue()
            }
        }.fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "空闲教室",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("磬苑", onClick = { campus = 1 }, selected = campus == 1)
                SoftChip("龙河", onClick = { campus = 2 }, selected = campus == 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("今天", onClick = { date = LocalDate.now().toString() }, selected = date == LocalDate.now().toString())
                SoftChip(
                    "明天",
                    onClick = { date = LocalDate.now().plusDays(1).toString() },
                    selected = date == LocalDate.now().plusDays(1).toString(),
                )
            }
        }
        AsyncContent(state, onRetry = { reload++ }) {
            val rooms = (state as LoadState.Ready).value
            if (rooms.isEmpty()) EmptyState("没有查到空闲教室。") else FeatureScrollColumn {
                Text(
                    "全天 1-13 节 · ${rooms.size} 间",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                rooms.forEach { room ->
                    PageCard {
                        Text(
                            room.replace(Regex("^(\\S+) \\1"), "$1"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NextPalette.Ink,
                        )
                    }
                }
            }
        }
    }
}


private fun courseStatus(status: String): String = when (status) {
    "PASSED" -> "已通过"
    "TAKING" -> "修读中"
    "UNREPAIRED" -> "未修"
    "FAILED" -> "未通过"
    else -> status
}
