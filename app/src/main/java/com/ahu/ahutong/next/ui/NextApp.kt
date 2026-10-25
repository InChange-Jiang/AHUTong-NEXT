package com.ahu.ahutong.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.app.data.SettingsRepository
import com.ahu.ahutong.next.reminder.CourseReminders
import com.ahu.ahutong.next.reminder.ElectricityAlerts
import com.ahu.ahutong.next.ui.chat.ChatScreen
import com.ahu.ahutong.next.ui.chat.ChatViewModel
import com.ahu.ahutong.next.ui.lab.ToolLabScreen
import com.ahu.ahutong.next.ui.nav.AppDrawer
import com.ahu.ahutong.next.ui.nav.AppRoute
import com.ahu.ahutong.next.ui.nav.rememberAppNav
import com.ahu.ahutong.next.ui.pages.AccountsPage
import com.ahu.ahutong.next.ui.pages.AssignmentsPage
import com.ahu.ahutong.next.ui.pages.AboutPage
import com.ahu.ahutong.next.ui.pages.AttendancePage
import com.ahu.ahutong.next.ui.pages.BillsPage
import com.ahu.ahutong.next.ui.pages.CalendarEventsPage
import com.ahu.ahutong.next.ui.pages.CanteenPage
import com.ahu.ahutong.next.ui.pages.CardPage
import com.ahu.ahutong.next.ui.pages.CompletionPage
import com.ahu.ahutong.next.ui.pages.CourseTasksPage
import com.ahu.ahutong.next.ui.pages.EvaluationPage
import com.ahu.ahutong.next.ui.pages.ExamsPage
import com.ahu.ahutong.next.ui.pages.FreeRoomsPage
import com.ahu.ahutong.next.ui.pages.GradesPage
import com.ahu.ahutong.next.ui.pages.LostFoundPage
import com.ahu.ahutong.next.ui.pages.MailPage
import com.ahu.ahutong.next.ui.pages.MaterialsPage
import com.ahu.ahutong.next.ui.pages.PhoneBookPage
import com.ahu.ahutong.next.ui.pages.NoticesPage
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
    val baseDensity = LocalDensity.current

    LaunchedEffect(Unit) {
        CourseReminders.schedule(context, repository)
        ElectricityAlerts.check(context, repository)
    }

    BackHandler(enabled = nav.canGoBack) { nav.pop() }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = nav.current == AppRoute.Chat,
        drawerContent = {
            AppDrawer(
                nav = nav,
                current = nav.current,
                onNewChat = {
                    viewModel.clearHistory()
                    nav.resetToChat()
                    closeDrawer()
                },
                onNavigate = { route ->
                    nav.push(route)
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
        ) {
            val back: () -> Unit = { nav.pop() }
            when (nav.current) {
                AppRoute.Chat -> ChatScreen(
                    state = state,
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::send,
                    onApprove = { viewModel.approve(it) },
                    onReject = viewModel::reject,
                    onCancel = viewModel::cancelRun,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    showSuggestions = suggestionsEnabled,
                )

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
                    onClearHistory = viewModel::clearHistory,
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
                AppRoute.Schedule -> SchedulePage(repository, back)
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
            }
        }
    }
}
