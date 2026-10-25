package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.ProgramCompletionGateway
import com.ahu.ahutong.data.attendance.AttendanceRecord
import com.ahu.ahutong.data.attendance.KqcardAttendanceRepository
import com.ahu.ahutong.data.crawler.model.jwxt.ProgramCompletion
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.app.data.AssignmentPlanner
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
import com.ahu.ahutong.next.ui.components.RefreshIconButton
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftChipStrip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SchedulePage(repository: NextCampusRepository, onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<List<CourseOccurrence>>>(LoadState.Loading) }
    var weekView by remember { mutableStateOf(true) }
    var weekPage by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.schedule(14) } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }

    FeatureScaffold(
        title = "课表",
        onBack = onBack,
        actions = { RefreshIconButton { reload++ } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val courses = (state as LoadState.Ready).value
            FeatureScrollColumn {
                SoftChipStrip {
                    SoftChip("周视图", onClick = { weekView = true }, selected = weekView)
                    SoftChip("列表", onClick = { weekView = false }, selected = !weekView)
                    if (weekView) {
                        SoftChip("本周", onClick = { weekPage = 0 }, selected = weekPage == 0)
                        SoftChip("下周", onClick = { weekPage = 1 }, selected = weekPage == 1)
                    }
                }
                if (courses.isEmpty()) {
                    EmptyState("这段时间没有课程。")
                } else if (weekView) {
                    WeekTimetable(courses, LocalDate.now().plusDays((weekPage * 7).toLong()))
                } else {
                    val grouped = courses.groupBy { it.date }
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
private fun WeekTimetable(courses: List<CourseOccurrence>, startDate: LocalDate) {
    val days = (0..6).map { startDate.plusDays(it.toLong()) }
    val dateSet = days.map { it.toString() }.toSet()
    val byDate = courses.filter { it.date in dateSet }.groupBy { it.date }
    val hourHeight = 64.dp
    val totalHeight = hourHeight * 14
    val gutter = 38.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val columnWidth = (maxWidth - gutter) / 7
            Column {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(gutter))
                days.forEach { day ->
                    val isToday = day == LocalDate.now()
                    Column(
                        modifier = Modifier.width(columnWidth).padding(bottom = 8.dp),
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
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.width(gutter).height(totalHeight)) {
                    (8..21).forEach { hour ->
                        Text(
                            "%02d".format(hour),
                            modifier = Modifier.offset(y = hourHeight * (hour - 8)),
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
                days.forEach { day ->
                    Box(Modifier.width(columnWidth).height(totalHeight)) {
                        byDate[day.toString()].orEmpty().forEach { course ->
                            CourseBlock(course, hourHeight)
                        }
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
    var loadedAt by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.grades(null) } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
        rank = runCatching { withContext(Dispatchers.IO) { LegacyCampusApi.gpaRank().requireValue() } }
            .getOrNull()
        loadedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }

    FeatureScaffold(
        title = "成绩单",
        onBack = onBack,
        actions = { RefreshIconButton { reload++ } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val all = (state as LoadState.Ready).value
            val semesterIds = all.groupBy { it.term }.mapValues { (_, list) -> list.maxOf { it.semesterId } }
            val terms = semesterIds.keys.sortedWith(
                compareByDescending<String> { semesterOrder(it) }
                    .thenByDescending { semesterIds[it] ?: 0 },
            )
            val activeTerm = selectedTerm?.takeIf { it in terms } ?: terms.firstOrNull()
            val grades = if (activeTerm == null) all else all.filter { it.term == activeTerm }
            val credits = grades.sumOf { it.credit }
            val weighted = if (credits <= 0.0) 0.0 else grades.sumOf { it.gpa * it.credit } / credits
            val semesterId = grades.maxOfOrNull { it.semesterId } ?: 0
            val semesterSummary = rank?.gpaSemesterSubs
                ?.firstOrNull { semesterId > 0 && it.semesterId == semesterId }
            val termGpa = semesterSummary?.gpa?.takeIf { it > 0.0 } ?: weighted
            val allCredits = all.sumOf { it.credit }
            val overallWeighted =
                if (allCredits <= 0.0) 0.0 else all.sumOf { it.gpa * it.credit } / allCredits
            val overallGpa = rank?.gpa?.takeIf { it > 0.0 } ?: overallWeighted
            val updatedText = rank?.updatedDateTimeStr?.takeIf { it.isNotBlank() }
                ?: loadedAt
                ?: "暂无"
            val headCount = rank?.majorHeadCount ?: 0
            val termRank = when {
                semesterSummary == null || semesterSummary.majorRank <= 0 -> "暂无"
                headCount > 0 -> "${semesterSummary.majorRank} / $headCount"
                else -> semesterSummary.majorRank.toString()
            }
            val overallRank = rank?.let {
                when {
                    it.majorRank <= 0 -> "暂无"
                    it.majorHeadCount > 0 -> "${it.majorRank} / ${it.majorHeadCount}"
                    else -> it.majorRank.toString()
                }
            } ?: "暂无"
            val isLatest = activeTerm == null || activeTerm == terms.firstOrNull()

            FeatureScrollColumn {
                if (terms.isNotEmpty()) {
                    SoftChipStrip {
                        terms.forEach { term ->
                            SoftChip(
                                term,
                                onClick = { selectedTerm = term },
                                selected = term == activeTerm,
                            )
                        }
                    }
                }

                PageCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isLatest) "本学期平均绩点" else "该学期平均绩点",
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                            Text(
                                String.format(Locale.CHINA, "%.2f", termGpa),
                                style = MaterialTheme.typography.headlineMedium,
                                color = NextPalette.AccentDeep,
                            )
                        }
                        Text(
                            buildString {
                                activeTerm?.let { append(it).append('\n') }
                                append("${credits.compact()} 学分 · ${grades.size} 门")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                            textAlign = TextAlign.End,
                        )
                    }
                    HorizontalDivider(color = NextPalette.Line)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradeMetric(
                            "全程平均绩点",
                            String.format(Locale.CHINA, "%.2f", overallGpa),
                            Modifier.weight(1f),
                        )
                        GradeMetric("全程专业排名", overallRank, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradeMetric("该学期专业排名", termRank, Modifier.weight(1f))
                        GradeMetric(
                            "最后更新时间",
                            updatedText,
                            Modifier.weight(1f),
                            valueStyle = MaterialTheme.typography.labelMedium,
                        )
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

private fun semesterOrder(term: String): Int {
    val match = Regex("(\\d{4})-(\\d{4}).*?第\\s*([12一二])").find(term) ?: return 0
    val year = match.groupValues[1].toIntOrNull() ?: return 0
    val number = when (match.groupValues[3]) {
        "1", "一" -> 1
        "2", "二" -> 2
        else -> 0
    }
    return year * 10 + number
}

@Composable
private fun GradeMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
        )
        Text(
            value,
            style = valueStyle,
            fontWeight = FontWeight.SemiBold,
            color = NextPalette.Ink,
            maxLines = 2,
        )
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
    var showFinished by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.exams() } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "考场查询",
        onBack = onBack,
        actions = { RefreshIconButton { reload++ } },
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
                    Text(
                        "待考 ${upcoming.size} 场 · 已结束 ${finished.size} 场",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    upcoming.forEach { ExamCard(it) }
                    if (finished.isNotEmpty()) {
                        TextButton(onClick = { showFinished = !showFinished }) {
                            Text(if (showFinished) "收起已结束 (${finished.size})" else "展开已结束 (${finished.size})")
                        }
                        if (showFinished) {
                            finished.forEach { ExamCard(it, past = true) }
                        }
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
    var planning by remember { mutableStateOf(false) }
    var planMessage by remember { mutableStateOf<String?>(null) }
    var showPlanConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload, days) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.assignments(days) } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "作业",
        onBack = onBack,
        actions = {
            RefreshIconButton { reload++ }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { showPlanConfirm = true }) { Text("智能排期") }
        },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val works = (state as LoadState.Ready).value
            FeatureScrollColumn {
                SoftChipStrip {
                    SoftChip("14 天", onClick = { days = 14 }, selected = days == 14)
                    SoftChip("30 天", onClick = { days = 30 }, selected = days == 30)
                }
                if (works.isEmpty()) {
                    EmptyState("这段时间没有作业。")
                } else {
                    planMessage?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = NextPalette.AccentDeep)
                    }
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

    if (showPlanConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPlanConfirm = false },
            title = { Text("把作业排进日历？") },
            text = {
                Text("按截止时间从早到晚，避开课程和已有日程，为每条未完成作业找 1 小时空闲时段写入本地日历。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showPlanConfirm = false
                    planning = true
                    planMessage = null
                    scope.launch {
                        runCatching {
                            val works = (state as? LoadState.Ready<List<Assignment>>)?.value.orEmpty()
                            withContext(Dispatchers.IO) { AssignmentPlanner.plan(repository, works) }
                        }.fold(
                            onSuccess = { result ->
                                planMessage = "已排入 ${result.scheduled.size} 条作业" +
                                    if (result.skipped > 0) "；${result.skipped} 条在截止前找不到空档" else ""
                            },
                            onFailure = { planMessage = it.message ?: "排期失败" },
                        )
                        planning = false
                    }
                }) { Text("开始排期") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPlanConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
fun AttendancePage(onBack: () -> Unit) {
    var page by remember { mutableIntStateOf(1) }
    var records by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<Unit>>(LoadState.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<Int?>(null) }
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
        title = "教务考勤",
        onBack = onBack,
        actions = { RefreshIconButton { load(1, false) } },
    ) {
        AsyncContent(state, onRetry = { load(1, false) }) {
            val visible = records.filter { statusFilter == null || it.status.code == statusFilter }
            FeatureScrollColumn {
                SoftChipStrip {
                    SoftChip("全部", onClick = { statusFilter = null }, selected = statusFilter == null)
                    listOf(1 to "正常", 2 to "迟到", 3 to "缺勤", 5 to "请假").forEach { (code, label) ->
                        SoftChip(label, onClick = { statusFilter = code }, selected = statusFilter == code)
                    }
                }
                if (visible.isEmpty()) {
                    EmptyState("暂无考勤记录。")
                } else {
                    Text(
                        "共 ${if (statusFilter == null) total else visible.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    visible.forEach { record ->
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
                                "第${record.section}节 · 打卡 ${record.punchTime.ifBlank { "—" }} · ${record.teacher}" +
                                    if (record.canAppeal) " · 可申诉" else "",
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
}

@Composable
fun CompletionPage(onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<ProgramCompletion>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var onlyUnfinished by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching {
            withContext(Dispatchers.IO) { ProgramCompletionGateway.refresh().requireValue().first }
        }.fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "培养方案",
        onBack = onBack,
        actions = { RefreshIconButton { reload++ } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val program = (state as LoadState.Ready).value
            FeatureScrollColumn {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("全部模块", onClick = { onlyUnfinished = false }, selected = !onlyUnfinished)
                    SoftChip("仅看未完成", onClick = { onlyUnfinished = true }, selected = onlyUnfinished)
                }
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
                program.modules.filter { !onlyUnfinished || it.result != "PASSED" }.forEach { module ->
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

private data class FreeRoomsQuery(
    val campus: Int,
    val date: String,
    val units: List<Int>,
    val building: String?,
)

@Composable
fun FreeRoomsPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var campus by remember { mutableIntStateOf(1) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var selectedUnits by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var buildings by remember { mutableStateOf<List<String>>(emptyList()) }
    var buildingFilter by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf<FreeRoomsQuery?>(null) }
    var state by remember { mutableStateOf<LoadState<List<String>>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val units = if (selectedUnits.isEmpty()) (1..13).toList() else selectedUnits.sorted()
    val loading = state is LoadState.Loading

    val confirm = {
        applied = FreeRoomsQuery(campus, date, units, buildingFilter)
        reload += 1
    }

    LaunchedEffect(reload) {
        val query = applied ?: return@LaunchedEffect
        state = LoadState.Loading
        state = runCatching {
            withContext(Dispatchers.IO) {
                LegacyCampusApi.freeRooms(
                    query.campus,
                    query.date,
                    query.units.map(Int::toString),
                ).requireValue()
            }
        }.fold(
            onSuccess = { rooms ->
                buildings = rooms.map { it.substringBefore(' ') }.distinct().sorted()
                LoadState.Ready(rooms)
            },
            onFailure = { it.toLoadFailure() },
        )
    }

    FeatureScaffold(
        title = "空闲教室",
        onBack = onBack,
        actions = { RefreshIconButton { if (!loading) confirm() } },
    ) {
        FeatureScrollColumn {
            PageCard {
                PageTitle("查询条件")
                Text("校区", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("磬苑", onClick = { campus = 1 }, selected = campus == 1)
                    SoftChip("龙河", onClick = { campus = 2 }, selected = campus == 2)
                }
                Text("日期", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("今天", onClick = { date = LocalDate.now().toString() }, selected = date == LocalDate.now().toString())
                    SoftChip("明天", onClick = { date = LocalDate.now().plusDays(1).toString() }, selected = date == LocalDate.now().plusDays(1).toString())
                    SoftChip("后天", onClick = { date = LocalDate.now().plusDays(2).toString() }, selected = date == LocalDate.now().plusDays(2).toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "时段",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    SoftChip("全天", onClick = { selectedUnits = emptySet() }, selected = selectedUnits.isEmpty())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { unit ->
                        SoftChip(
                            unit.toString(),
                            onClick = { selectedUnits = selectedUnits.toggle(unit) },
                            selected = unit in selectedUnits,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (8..13).forEach { unit ->
                        SoftChip(
                            unit.toString(),
                            onClick = { selectedUnits = selectedUnits.toggle(unit) },
                            selected = unit in selectedUnits,
                        )
                    }
                }
                if (buildings.isNotEmpty()) {
                    Text("教学楼", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                    SoftChipStrip {
                        SoftChip("全部", onClick = { buildingFilter = null }, selected = buildingFilter == null)
                        buildings.forEach { building ->
                            SoftChip(building, onClick = { buildingFilter = building }, selected = buildingFilter == building)
                        }
                    }
                }
                SoftPrimaryButton(
                    "确认筛选",
                    onClick = confirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                )
                if (state == null) {
                    Text(
                        "选好条件后点「确认筛选」再查询。",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            }
            val current = state
            if (current != null) {
                AsyncContent(current, onRetry = { confirm() }) {
                    val query = applied
                    val rooms = (current as LoadState.Ready).value
                        .filter { room -> query?.building?.let { room.startsWith("$it ") } ?: true }
                    if (rooms.isEmpty()) EmptyState("没有查到空闲教室。") else {
                        val shownUnits = query?.units ?: units
                        PageCard {
                            Text(
                                "${shownUnits.first()}-${shownUnits.last()} 节 · ${rooms.size} 间",
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                            rooms.forEach { room ->
                                Text(
                                    room.replace(Regex("^(\\S+) \\1"), "$1"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NextPalette.Ink,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Set<Int>.toggle(value: Int): Set<Int> =
    if (contains(value)) this - value else this + value


private fun courseStatus(status: String): String = when (status) {
    "PASSED" -> "已通过"
    "TAKING" -> "修读中"
    "UNREPAIRED" -> "未修"
    "FAILED" -> "未通过"
    else -> status
}
