package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.CustomCourseStore
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.app.data.ScheduleCache
import com.ahu.ahutong.next.app.data.ScheduleCacheStore
import com.ahu.ahutong.next.app.data.SchedulePrefs
import com.ahu.ahutong.next.app.data.toCached
import com.ahu.ahutong.next.app.data.toCourse
import com.ahu.ahutong.next.app.data.toCourses
import com.ahu.ahutong.next.app.data.toSemester
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.EmptyState
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.RefreshIconButton
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val TOTAL_WEEKS = 20
private const val SECTION_COUNT = 13
private val SECTION_HEIGHT = 54.dp
private val GRID_GUTTER = 34.dp

private val scheduleColors = listOf(
    Color(0xFF5B8DEF),
    Color(0xFF62B58F),
    Color(0xFFE5A64B),
    Color(0xFFB07ACC),
    Color(0xFFE07A7A),
    Color(0xFF4FA9C9),
    Color(0xFF9BAE4C),
    Color(0xFF7B8BC4),
)

private data class SemesterSchedule(
    val semesters: List<ScheduleSemester>,
    val currentSemesterId: Int,
    val semesterId: Int,
    val courses: List<Course>,
    val startDate: LocalDate?,
    val currentWeek: Int,
)

private data class PlacedCourse(
    val course: Course,
    val lane: Int,
    val laneCount: Int,
)

@Composable
fun SemesterSchedulePage(
    repository: NextCampusRepository,
    onBack: () -> Unit,
    onMenu: (() -> Unit)? = null,
    onOpenCustomCourses: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { SchedulePrefs(context) }
    val customStore = remember { CustomCourseStore(context) }
    val cacheStore = remember { ScheduleCacheStore(context) }
    val accountId = remember {
        LegacyCampusApi.currentStudentId().orEmpty().ifBlank { "anon" }
    }
    var customCourses by remember { mutableStateOf(customStore.load(accountId)) }
    var showSaturday by remember { mutableStateOf(prefs.showSaturday()) }
    var showSunday by remember { mutableStateOf(prefs.showSunday()) }
    var showNowLine by remember { mutableStateOf(prefs.showNowLine()) }
    var overview by remember { mutableStateOf(false) }

    var state by remember { mutableStateOf<LoadState<SemesterSchedule>>(LoadState.Loading) }
    var selectedSemesterId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedWeek by rememberSaveable { mutableIntStateOf(1) }
    var weekInitialized by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshRequested by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Course?>(null) }

    LaunchedEffect(reload) {
        val hadData = state is LoadState.Ready
        if (!hadData) state = LoadState.Loading
        if (refreshRequested) refreshing = true
        val result = runCatching {
            withContext(Dispatchers.IO) {
                loadSemesterSchedule(
                    requestedSemesterId = selectedSemesterId,
                    cacheStore = cacheStore,
                    accountId = accountId,
                    forceRefresh = refreshRequested,
                )
            }
        }
        val loaded = result.getOrNull()
        if (loaded != null) {
            state = LoadState.Ready(loaded)
        } else if (!hadData) {
            state = result.exceptionOrNull()?.toLoadFailure()
                ?: LoadState.Failed("课表加载失败")
        }
        refreshRequested = false
        refreshing = false
        val ready = state
        if (ready is LoadState.Ready) {
            if (selectedSemesterId == null) selectedSemesterId = ready.value.semesterId
            if (!weekInitialized) {
                selectedWeek = ready.value.currentWeek
                weekInitialized = true
            }
        }
    }

    FeatureScaffold(
        title = "课表",
        onBack = onBack,
        onMenu = onMenu,
        actions = {
            SoftIconButton(onClick = {
                val ready = state as? LoadState.Ready
                if (ready != null) {
                    selectedWeek = ready.value.currentWeek
                    overview = false
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule_today),
                    contentDescription = "回到本周",
                    tint = NextPalette.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            SoftIconButton(onClick = { showSettings = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_iconpark_setting),
                    contentDescription = "课表设置",
                    tint = NextPalette.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            SoftIconButton(onClick = {
                if (!refreshing) {
                    refreshRequested = true
                    reload++
                }
            }) {
                if (refreshing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NextPalette.Accent,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconpark_refresh),
                        contentDescription = "刷新课表",
                        tint = NextPalette.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        AsyncContent(state, onRetry = {
            refreshRequested = true
            reload++
        }) {
            val data = (state as LoadState.Ready).value
            val semester = data.semesters.firstOrNull { it.id == data.semesterId }
                ?: data.semesters.firstOrNull()
            val isCurrentSemester = semester?.isCurrent == true
            val semesterCustomCourses = remember(customCourses, data.semesterId) {
                customCourses.filter { it.semesterId == data.semesterId }
                    .flatMap { it.toCourses() }
            }
            val allCourses = remember(data.courses, semesterCustomCourses) {
                data.courses + semesterCustomCourses
            }
            val weekStripState = rememberLazyListState()
            val density = androidx.compose.ui.platform.LocalDensity.current
            LaunchedEffect(selectedWeek) {
                withFrameNanos { }
                val layout = weekStripState.layoutInfo
                val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
                val itemPx = with(density) { 40.dp.toPx() }
                if (viewport > 0f) {
                    weekStripState.animateScrollToItem(
                        index = (selectedWeek - 1).coerceAtLeast(0),
                        scrollOffset = -((viewport - itemPx) / 2f).toInt(),
                    )
                } else {
                    weekStripState.animateScrollToItem((selectedWeek - 1).coerceAtLeast(0))
                }
            }
            val visibleDays = remember(showSaturday, showSunday) {
                buildList {
                    (1..5).forEach(::add)
                    if (showSaturday) add(6)
                    if (showSunday) add(7)
                }
            }
            val weekCourses = remember(allCourses, selectedWeek, overview) {
                if (overview) allCourses
                else allCourses.filter { course ->
                    course.weekIndexes.isEmpty() || selectedWeek in course.weekIndexes
                }
            }
            val now by produceState(initialValue = LocalTime.now()) {
                while (true) {
                    value = LocalTime.now()
                    delay(60_000L)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LazyRow(
                    state = weekStripState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items((1..TOTAL_WEEKS).toList()) { week ->
                        val selected = week == selectedWeek
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (selected) NextPalette.AccentSoft else Color.Transparent,
                                )
                                .clickable { selectedWeek = week },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$week",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) {
                                    NextPalette.AccentDeep
                                } else {
                                    NextPalette.Ink
                                },
                            )
                        }
                    }
                }

                if (allCourses.isEmpty()) {
                    EmptyState("本学期暂无课程")
                } else {
                    WeekGridView(
                        courses = weekCourses,
                        days = visibleDays,
                        startDate = data.startDate,
                        week = selectedWeek,
                        showNowLine = showNowLine &&
                            isCurrentSemester &&
                            selectedWeek == data.currentWeek,
                        now = now,
                        onCourseClick = { detailCourse = it },
                        customColors = remember(customCourses) {
                            customCourses.mapNotNull { record ->
                                record.colorArgb?.let { record.name to it }
                            }.toMap()
                        },
                    )
                    Spacer(Modifier.height(76.dp))
                }
            }
        }
    }

    if (showSettings) {
        ScheduleSettingsDialog(
            semesters = (state as? LoadState.Ready)?.value?.semesters.orEmpty(),
            currentSemesterId = (state as? LoadState.Ready)?.value?.semesterId,
            customCourseCount = (state as? LoadState.Ready)?.value?.semesterId?.let { id ->
                customCourses.count { it.semesterId == id }
            } ?: 0,
            showSaturday = showSaturday,
            showSunday = showSunday,
            showNowLine = showNowLine,
            overview = overview,
            onShowSaturday = {
                showSaturday = it
                prefs.setShowSaturday(it)
            },
            onShowSunday = {
                showSunday = it
                prefs.setShowSunday(it)
            },
            onShowNowLine = {
                showNowLine = it
                prefs.setShowNowLine(it)
            },
            onOverview = { overview = it },
            onOpenCustomCourses = {
                showSettings = false
                onOpenCustomCourses()
            },
            onSelectSemester = { target ->
                val ready = state as? LoadState.Ready ?: return@ScheduleSettingsDialog
                selectedSemesterId = target.id
                selectedWeek = if (target.isCurrent) ready.value.currentWeek else 1
                overview = false
                showSettings = false
                state = LoadState.Loading
                reload++
            },
            onDismiss = { showSettings = false },
        )
    }

    detailCourse?.let { course ->
        CourseDetailDialog(course = course, onDismiss = { detailCourse = null })
    }
}

private suspend fun loadSemesterSchedule(
    requestedSemesterId: Int?,
    cacheStore: ScheduleCacheStore,
    accountId: String,
    forceRefresh: Boolean,
): SemesterSchedule {
    val cached = cacheStore.load(accountId)
    val localConfig = runCatching {
        CurrentWeekResolver.resolveLocalConfig()?.config
    }.getOrNull()
    val requested = requestedSemesterId
        ?: cached?.semesters?.firstOrNull { it.isCurrent }?.id
        ?: cached?.semesters?.firstOrNull()?.id

    if (!forceRefresh && cached != null && requested != null &&
        cached.coursesBySemester.containsKey(requested.toString())
    ) {
        return buildFromCache(cached, requested, localConfig)
    }

    val semesters = LegacyCampusApi.scheduleSemesters().valueOrThrow()
    if (semesters.isEmpty()) throw IllegalStateException("教务没有返回学期信息")
    val current = semesters.firstOrNull { it.isCurrent } ?: semesters.first()
    val semester = semesters.firstOrNull { it.id == requested } ?: current
    val courses = LegacyCampusApi.semesterSchedule(semester.id).valueOrThrow()
    val config = localConfig ?: runCatching {
        CurrentWeekResolver.resolveLocalFirst().config
    }.getOrNull()
    val currentWeek = config?.week?.coerceIn(1, TOTAL_WEEKS)
        ?: cached?.currentWeek?.coerceIn(1, TOTAL_WEEKS)
        ?: 1
    val newCache = ScheduleCache(
        semesters = semesters.map { it.toCached() },
        coursesBySemester = cached?.coursesBySemester.orEmpty() +
            (semester.id.toString() to courses.map { it.toCached() }),
        currentWeek = currentWeek,
        updatedAt = System.currentTimeMillis(),
    )
    cacheStore.save(accountId, newCache)
    val startDate = parseDate(semester.startDate)
        ?: if (semester.isCurrent) {
            runCatching {
                config?.startTime?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
            }.getOrNull()
        } else {
            null
        }
    return SemesterSchedule(
        semesters = semesters,
        currentSemesterId = current.id,
        semesterId = semester.id,
        courses = courses,
        startDate = startDate,
        currentWeek = currentWeek,
    )
}

private fun buildFromCache(
    cache: ScheduleCache,
    requestedSemesterId: Int,
    config: com.ahu.ahutong.data.model.ScheduleConfigBean?,
): SemesterSchedule {
    val semesters = cache.semesters.map { it.toSemester() }
    val current = semesters.firstOrNull { it.isCurrent } ?: semesters.first()
    val semester = semesters.firstOrNull { it.id == requestedSemesterId } ?: current
    val courses = cache.coursesBySemester[semester.id.toString()]
        .orEmpty()
        .map { it.toCourse() }
    val currentWeek = config?.week?.coerceIn(1, TOTAL_WEEKS)
        ?: cache.currentWeek.coerceIn(1, TOTAL_WEEKS)
    val startDate = parseDate(semester.startDate)
        ?: if (semester.isCurrent) {
            runCatching {
                config?.startTime?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
            }.getOrNull()
        } else {
            null
        }
    return SemesterSchedule(
        semesters = semesters,
        currentSemesterId = current.id,
        semesterId = semester.id,
        courses = courses,
        startDate = startDate,
        currentWeek = currentWeek,
    )
}

private fun parseDate(text: String?): LocalDate? =
    text?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
    }

private fun <T> AhuResult<T>.valueOrThrow(): T = when (this) {
    is AhuResult.Success -> value
    is AhuResult.Failure -> throw IllegalStateException(error.toChinese())
}

@Composable
private fun WeekGridView(
    courses: List<Course>,
    days: List<Int>,
    startDate: LocalDate?,
    week: Int,
    showNowLine: Boolean,
    now: LocalTime,
    onCourseClick: (Course) -> Unit,
    customColors: Map<String, Int>,
) {
    val byDay = remember(courses, days, week) {
        days.associateWith { day ->
            layoutDay(courses.filter { it.weekday == day })
        }
    }
    val today = LocalDate.now()
    val dayDates = remember(startDate, week) {
        startDate?.let { start ->
            (1..7).associateWith { day -> start.plusDays((week - 1) * 7L + day - 1) }
        } ?: emptyMap()
    }

    PageCard {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val contentWidth = maxWidth - GRID_GUTTER
            val columnWidth = contentWidth / days.size
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(GRID_GUTTER))
                    days.forEach { day ->
                        val date = dayDates[day]
                        val isToday = date == today
                        Column(
                            modifier = Modifier.width(columnWidth).padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                weekdayShort(day),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) NextPalette.AccentDeep else NextPalette.InkSecondary,
                            )
                            Text(
                                date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isToday) NextPalette.AccentDeep else NextPalette.InkSecondary,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.width(GRID_GUTTER)) {
                        repeat(SECTION_COUNT) { index ->
                            Box(
                                modifier = Modifier.height(SECTION_HEIGHT).fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = NextPalette.InkSecondary,
                                )
                            }
                        }
                    }
                    days.forEach { day ->
                        val date = dayDates[day]
                        val isToday = date == today
                        val nowFraction = remember(now, isToday, showNowLine) {
                            if (isToday && showNowLine) nowFraction(now) else null
                        }
                        Box(
                            modifier = Modifier
                                .width(columnWidth)
                                .height(SECTION_HEIGHT * SECTION_COUNT)
                                .drawBehind {
                                    repeat(SECTION_COUNT + 1) { index ->
                                        val y = size.height * index / SECTION_COUNT
                                        drawLine(
                                            color = NextPalette.Line.copy(alpha = 0.6f),
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = 1f,
                                        )
                                    }
                                    if (isToday) {
                                        drawRect(NextPalette.Accent.copy(alpha = 0.035f))
                                    }
                                    nowFraction?.let { fraction ->
                                        val y = size.height * fraction
                                        drawLine(
                                            color = NextPalette.Accent,
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = 2.dp.toPx(),
                                        )
                                    }
                                },
                        ) {
                            byDay[day].orEmpty().forEach { placed ->
                                CourseBlock(
                                    placed = placed,
                                    columnWidth = columnWidth,
                                    color = customColors[placed.course.name]?.let { Color(it) },
                                    onClick = { onCourseClick(placed.course) },
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
private fun CourseBlock(
    placed: PlacedCourse,
    columnWidth: Dp,
    color: Color?,
    onClick: () -> Unit,
) {
    val course = placed.course
    val color = color ?: scheduleColors[(course.name.hashCode() and Int.MAX_VALUE) % scheduleColors.size]
    val top = ((course.startTime - 1).coerceAtLeast(0) * SECTION_HEIGHT.value).dp
    val height = (course.length.coerceAtLeast(1) * SECTION_HEIGHT.value - 2f).coerceAtLeast(26f).dp
    val laneWidth = 1f / placed.laneCount.coerceAtLeast(1)
    Box(
        modifier = Modifier
            .offset(x = columnWidth * (placed.lane * laneWidth), y = top)
            .fillMaxWidth(laneWidth)
            .height(height)
            .padding(start = 1.dp, end = 1.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 3.dp),
    ) {
        Column {
            Text(
                course.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = if (height > 70.dp) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank() && height > 44.dp) {
                Text(
                    course.location,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = NextPalette.InkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ScheduleSettingsDialog(
    semesters: List<ScheduleSemester>,
    currentSemesterId: Int?,
    customCourseCount: Int,
    showSaturday: Boolean,
    showSunday: Boolean,
    showNowLine: Boolean,
    overview: Boolean,
    onShowSaturday: (Boolean) -> Unit,
    onShowSunday: (Boolean) -> Unit,
    onShowNowLine: (Boolean) -> Unit,
    onOverview: (Boolean) -> Unit,
    onOpenCustomCourses: () -> Unit,
    onSelectSemester: (ScheduleSemester) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课表设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingSwitchRow(
                    "显示周六",
                    "关闭后隐藏周六课程，剩余天自动均分宽度",
                    showSaturday,
                    onShowSaturday,
                )
                SettingSwitchRow(
                    "显示周日",
                    "关闭后隐藏周日课程，剩余天自动均分宽度",
                    showSunday,
                    onShowSunday,
                )
                SettingSwitchRow(
                    "显示当前时间线",
                    "在课表上标记现在的时间，直观显示当前课程进度",
                    showNowLine,
                    onShowNowLine,
                )
                SettingSwitchRow(
                    "总览课表",
                    "显示全部周次的课程，重叠课程会平分同一块时间区域",
                    overview,
                    onOverview,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenCustomCourses)
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "自定义课程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NextPalette.Ink,
                        )
                        Text(
                            "加课、晚自习等教务没有的课，只存本机",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                    Text(
                        if (customCourseCount > 0) "$customCourseCount 门" else "添加",
                        style = MaterialTheme.typography.labelMedium,
                        color = NextPalette.AccentDeep,
                    )
                }

                HorizontalDivider(color = NextPalette.Line)
                Text(
                    "学期",
                    style = MaterialTheme.typography.labelMedium,
                    color = NextPalette.InkSecondary,
                )
                if (semesters.isEmpty()) {
                    Text(
                        "学期列表还没加载出来，先刷新课表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        semesters.forEach { semester ->
                            val selected = semester.id == currentSemesterId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) NextPalette.AccentSoft else Color.Transparent,
                                    )
                                    .clickable { onSelectSemester(semester) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    semester.name.ifBlank { "学期 ${semester.id}" },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) NextPalette.AccentDeep else NextPalette.Ink,
                                )
                                if (semester.isCurrent) {
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NextPalette.AccentDeep,
                                    )
                                }
                                if (selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "✓",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NextPalette.AccentDeep,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun SettingSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = NextPalette.Ink,
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = NextPalette.InkSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CourseDetailDialog(course: Course, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                course.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("时间", sectionTimeLabel(course))
                DetailRow("周次", weeksLabel(course.weekIndexes))
                if (course.location.isNotBlank()) DetailRow("地点", course.location)
                if (course.teacher.isNotBlank()) DetailRow("教师", course.teacher)
                if (course.courseId.isNotBlank()) DetailRow("课程号", course.courseId)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.width(54.dp),
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

private fun sectionTimeLabel(course: Course): String {
    val range = ScheduleSectionTimes.getCourseClockRange(course)
    val sections = "第${course.startTime}-${course.startTime + course.length - 1}节"
    return if (range != null) "$sections · $range" else sections
}

private fun weeksLabel(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "每周"
    val sorted = weeks.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var previous = start
    sorted.drop(1).forEach { week ->
        if (week == previous + 1) {
            previous = week
        } else {
            parts += if (start == previous) "$start" else "$start-$previous"
            start = week
            previous = week
        }
    }
    parts += if (start == previous) "$start" else "$start-$previous"
    return "第${parts.joinToString("、")}周"
}

private fun layoutDay(courses: List<Course>): List<PlacedCourse> {
    if (courses.isEmpty()) return emptyList()
    val result = mutableListOf<PlacedCourse>()
    val cluster = mutableListOf<Course>()
    var clusterEnd = 0

    fun flush() {
        if (cluster.isEmpty()) return
        val laneEnds = mutableListOf<Int>()
        val placements = mutableListOf<Pair<Course, Int>>()
        cluster.sortedBy { it.startTime }.forEach { course ->
            val lane = laneEnds.indexOfFirst { course.startTime >= it }
            if (lane >= 0) {
                laneEnds[lane] = course.startTime + course.length
                placements += course to lane
            } else {
                laneEnds += course.startTime + course.length
                placements += course to (laneEnds.size - 1)
            }
        }
        result += placements.map { (course, lane) ->
            PlacedCourse(course, lane, laneEnds.size)
        }
        cluster.clear()
    }

    courses.sortedBy { it.startTime }.forEach { course ->
        if (cluster.isNotEmpty() && course.startTime >= clusterEnd) flush()
        cluster += course
        clusterEnd = maxOf(clusterEnd, course.startTime + course.length)
    }
    flush()
    return result
}

private fun nowFraction(now: LocalTime): Float? {
    val start = LocalTime.of(8, 0)
    val end = LocalTime.of(21, 25)
    if (now < start || now > end) return null
    val total = 13 * 60 + 25
    val elapsed = (now.toSecondOfDay() - start.toSecondOfDay()) / 60f
    return (elapsed / total).coerceIn(0f, 1f)
}

private fun weekdayShort(day: Int): String = when (day) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
