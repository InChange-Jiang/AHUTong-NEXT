package com.ahu.ahutong.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.app.data.SettingsRepository
import com.ahu.ahutong.next.reminder.CourseReminders
import com.ahu.ahutong.next.reminder.ElectricityAlerts
import com.ahu.ahutong.next.ui.chat.ChatScreen
import com.ahu.ahutong.next.ui.chat.ChatViewModel
import com.ahu.ahutong.next.ui.components.LocalNextToast
import com.ahu.ahutong.next.ui.components.NextToast
import com.ahu.ahutong.next.ui.lab.ToolLabScreen
import com.ahu.ahutong.next.ui.nav.AppDrawer
import com.ahu.ahutong.next.ui.nav.AppRoute
import com.ahu.ahutong.next.ui.nav.NavDirection
import com.ahu.ahutong.next.ui.nav.rememberAppNav
import com.ahu.ahutong.next.ui.pages.AboutPage
import com.ahu.ahutong.next.ui.pages.AccountsPage
import com.ahu.ahutong.next.ui.pages.AssignmentsPage
import com.ahu.ahutong.next.ui.pages.AttendancePage
import com.ahu.ahutong.next.ui.pages.BillsPage
import com.ahu.ahutong.next.ui.pages.CalendarEventsPage
import com.ahu.ahutong.next.ui.pages.CanteenPage
import com.ahu.ahutong.next.ui.pages.CardPage
import com.ahu.ahutong.next.ui.pages.CompletionPage
import com.ahu.ahutong.next.ui.pages.CourseTasksPage
import com.ahu.ahutong.next.ui.pages.CustomCoursesPage
import com.ahu.ahutong.next.ui.pages.EvaluationPage
import com.ahu.ahutong.next.ui.pages.ExamsPage
import com.ahu.ahutong.next.ui.pages.FreeRoomsPage
import com.ahu.ahutong.next.ui.pages.GradesPage
import com.ahu.ahutong.next.ui.pages.HistoryPage
import com.ahu.ahutong.next.ui.pages.LostFoundPage
import com.ahu.ahutong.next.ui.pages.MailPage
import com.ahu.ahutong.next.ui.pages.MaterialsPage
import com.ahu.ahutong.next.ui.pages.NoticesPage
import com.ahu.ahutong.next.ui.pages.PhoneBookPage
import com.ahu.ahutong.next.ui.pages.ProfilePage
import com.ahu.ahutong.next.ui.pages.RechargeBathroomPage
import com.ahu.ahutong.next.ui.pages.RechargeCardPage
import com.ahu.ahutong.next.ui.pages.RechargeElectricityPage
import com.ahu.ahutong.next.ui.pages.RechargeNetworkPage
import com.ahu.ahutong.next.ui.pages.RemindersPage
import com.ahu.ahutong.next.ui.pages.SchedulePage
import com.ahu.ahutong.next.ui.pages.SchoolCalendarPage
import com.ahu.ahutong.next.ui.pages.UpdatesPage
import com.ahu.ahutong.next.ui.pages.WeatherPage
import com.ahu.ahutong.next.ui.settings.SettingsScreen
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun NextApp(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val nav = rememberAppNav()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val repository = remember { NextCampusRepository() }

    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var displayScale by remember { mutableFloatStateOf(settings.displayScale()) }
    var suggestionsEnabled by remember { mutableStateOf(settings.suggestionsEnabled()) }
    val pagerState = rememberPagerState(pageCount = { TOP_LEVEL_PAGE_COUNT })
    val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val baseDensity = LocalDensity.current
    var indicatorVisible by remember { mutableStateOf(true) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastSeq by remember { mutableIntStateOf(0) }
    val showToast: (String) -> Unit = { message ->
        if (message.isNotBlank()) {
            toastMessage = message
            toastSeq++
        }
    }
    LaunchedEffect(toastSeq) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                indicatorVisible = true
            } else {
                delay(1400)
                indicatorVisible = false
            }
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        indicatorVisible = true
    }

    LaunchedEffect(Unit) {
        CourseReminders.schedule(context, repository)
        ElectricityAlerts.check(context, repository)
    }

    BackHandler(enabled = nav.canGoBack || pagerState.currentPage != 0) {
        if (!nav.pop()) {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val gotoPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen ||
            (nav.current == AppRoute.Chat && pagerState.currentPage == 0),
        drawerContent = {
            AppDrawer(
                nav = nav,
                current = nav.current,
                onNewChat = {
                    viewModel.newChat()
                    nav.resetToChat()
                    closeDrawer()
                    gotoPage(0)
                },
                onNavigate = { route ->
                    when (route) {
                        AppRoute.Schedule -> {
                            nav.resetToChat()
                            gotoPage(1)
                        }
                        AppRoute.CalendarEvents -> {
                            nav.resetToChat()
                            gotoPage(2)
                        }
                        else -> nav.push(route)
                    }
                    closeDrawer()
                },
            )
        },
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density * displayScale,
                fontScale = baseDensity.fontScale,
            ),
            LocalNextToast provides showToast,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = {
                    val push = nav.direction == NavDirection.PUSH ||
                        nav.direction == NavDirection.RESET
                    val enter = if (push) {
                        slideInHorizontally { it / 4 } + fadeIn()
                    } else {
                        slideInHorizontally { -it / 5 } + fadeIn()
                    }
                    val exit = if (push) {
                        slideOutHorizontally { -it / 6 } + fadeOut()
                    } else {
                        slideOutHorizontally { it / 5 } + fadeOut()
                    }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "route",
            ) { route ->
                if (route == AppRoute.Chat) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = TOP_LEVEL_PAGE_COUNT,
                    ) { page ->
                        when (page) {
                            0 -> ChatScreen(
                                state = state,
                                onInputChange = viewModel::onInputChange,
                                onSend = viewModel::send,
                                onApprove = { viewModel.approve(it) },
                                onReject = viewModel::reject,
                                onCancel = viewModel::cancelRun,
                                onOpenDrawer = openDrawer,
                                onOpenRechargeCard = { nav.push(AppRoute.RechargeCard) },
                                onOpenBills = { nav.push(AppRoute.Bills) },
                                showSuggestions = suggestionsEnabled,
                                listState = chatListState,
                            )

                            1 -> SchedulePage(
                                repository = repository,
                                onBack = {},
                                onMenu = openDrawer,
                                onOpenCustomCourses = { nav.push(AppRoute.CustomCourses) },
                            )

                            else -> CalendarEventsPage(
                                repository = repository,
                                onBack = {},
                                onMenu = openDrawer,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = indicatorVisible,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 6.dp),
                        enter = fadeIn() + scaleIn(initialScale = 0.92f),
                        exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    ) {
                        PageIndicator(
                            current = pagerState.currentPage,
                            onSelect = gotoPage,
                        )
                    }
                }
            } else {
                val back: () -> Unit = { nav.pop() }
                when (route) {
                    AppRoute.Settings -> SettingsScreen(
                        modelLabel = state.modelLabel,
                        historyCount = state.items.size,
                        displayScale = displayScale,
                        onDisplayScaleChange = {
                            displayScale = it
                            settings.setDisplayScale(it)
                        },
                        onBack = back,
                        onSaved = viewModel::refreshSettings,
                        onClearHistory = viewModel::clearAllHistory,
                        onOpenLab = { nav.push(AppRoute.ToolLab) },
                        suggestionsEnabled = suggestionsEnabled,
                        onSuggestionsChange = {
                            suggestionsEnabled = it
                            settings.setSuggestionsEnabled(it)
                        },
                        onOpenAccounts = { nav.push(AppRoute.Accounts) },
                        onOpenReminders = { nav.push(AppRoute.Reminders) },
                        onOpenUpdates = { nav.push(AppRoute.Updates) },
                        onOpenAbout = { nav.push(AppRoute.About) },
                    )

                    AppRoute.History -> HistoryPage(
                        sessions = state.sessions,
                        currentSessionId = state.currentSessionId,
                        onBack = back,
                        onOpen = { sessionId ->
                            viewModel.openSession(sessionId)
                            nav.resetToChat()
                            gotoPage(0)
                        },
                        onDelete = viewModel::deleteSession,
                        onNewChat = {
                            viewModel.newChat()
                            nav.resetToChat()
                            gotoPage(0)
                        },
                    )

                    AppRoute.ToolLab -> ToolLabScreen(onBack = back)
                    AppRoute.Profile -> ProfilePage(
                        onBack = back,
                        onOpenAccounts = { nav.push(AppRoute.Accounts) },
                        onLoggedOut = {
                            viewModel.refreshSettings()
                            nav.resetToChat()
                        },
                    )
                    AppRoute.Accounts -> AccountsPage(
                        onBack = back,
                        onAccountChanged = {
                            viewModel.refreshSettings()
                            nav.resetToChat()
                        },
                    )
                    AppRoute.Schedule -> SchedulePage(
                        repository = repository,
                        onBack = back,
                        onOpenCustomCourses = { nav.push(AppRoute.CustomCourses) },
                    )
                    AppRoute.CustomCourses -> CustomCoursesPage(onBack = back)
                    AppRoute.Grades -> GradesPage(repository, back)
                    AppRoute.Exams -> ExamsPage(repository, back)
                    AppRoute.Assignments -> AssignmentsPage(repository, back)
                    AppRoute.Attendance -> AttendancePage(back)
                    AppRoute.Completion -> CompletionPage(back)
                    AppRoute.FreeRooms -> FreeRoomsPage(repository, back)
                    AppRoute.CourseTasks -> CourseTasksPage(back)
                    AppRoute.PhoneBook -> PhoneBookPage(back)
                    AppRoute.Notices -> NoticesPage(back)
                    AppRoute.LostFound -> LostFoundPage(back)
                    AppRoute.Weather -> WeatherPage(back)
                    AppRoute.Card -> CardPage(repository, back) { nav.push(it) }
                    AppRoute.Bills -> BillsPage(repository, back)
                    AppRoute.RechargeCard -> RechargeCardPage(repository, back)
                    AppRoute.RechargeElectricity -> RechargeElectricityPage(repository, back) {
                        nav.push(AppRoute.Reminders)
                    }
                    AppRoute.RechargeBathroom -> RechargeBathroomPage(repository, back)
                    AppRoute.RechargeNetwork -> RechargeNetworkPage(repository, back)
                    AppRoute.Evaluation -> EvaluationPage(back)
                    AppRoute.SchoolCalendar -> SchoolCalendarPage(back)
                    AppRoute.Canteen -> CanteenPage(back)
                    AppRoute.Mail -> MailPage(back)
                    AppRoute.Materials -> MaterialsPage(back)
                    AppRoute.CalendarEvents -> CalendarEventsPage(repository, back)
                    AppRoute.Reminders -> RemindersPage(repository, back)
                    AppRoute.Updates -> UpdatesPage(back)
                    AppRoute.About -> AboutPage(back)
                    AppRoute.Chat -> Unit
                }
            }
            }
            NextToast(
                message = toastMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = if (nav.current == AppRoute.Chat) 92.dp else 28.dp,
                    ),
            )
            }
        }
    }
}

private const val TOP_LEVEL_PAGE_COUNT = 3

@Composable
private fun PageIndicator(
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(NextPalette.Surface.copy(alpha = 0.86f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(TOP_LEVEL_PAGE_COUNT) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 17.dp else 6.dp)
            val color by animateColorAsState(
                if (active) NextPalette.AccentDeep else NextPalette.InkSecondary.copy(alpha = 0.32f),
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(index) },
            )
        }
    }
}
