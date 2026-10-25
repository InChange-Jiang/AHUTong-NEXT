package com.ahu.ahutong.next.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

sealed interface AppRoute {
    data object Chat : AppRoute
    data object Settings : AppRoute
    data object ToolLab : AppRoute
    data object Profile : AppRoute
    data object Accounts : AppRoute
    data object Schedule : AppRoute
    data object Grades : AppRoute
    data object Exams : AppRoute
    data object Assignments : AppRoute
    data object CourseTasks : AppRoute
    data object Attendance : AppRoute
    data object Completion : AppRoute
    data object FreeRooms : AppRoute
    data object Notices : AppRoute
    data object LostFound : AppRoute
    data object Weather : AppRoute
    data object Card : AppRoute
    data object Bills : AppRoute
    data object Electricity : AppRoute
    data object RechargeCard : AppRoute
    data object RechargeElectricity : AppRoute
    data object RechargeBathroom : AppRoute
    data object RechargeNetwork : AppRoute
    data object Evaluation : AppRoute
    data object SchoolCalendar : AppRoute
    data object Canteen : AppRoute
    data object PhoneBook : AppRoute
    data object Mail : AppRoute
    data object Materials : AppRoute
    data object CalendarEvents : AppRoute
    data object Reminders : AppRoute
    data object Updates : AppRoute
    data object About : AppRoute
}

class AppNavState(initial: AppRoute = AppRoute.Chat) {
    val stack = mutableStateListOf(initial)

    val current: AppRoute get() = stack.last()

    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: AppRoute) {
        if (route == current) return
        stack.add(route)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun resetToChat() {
        stack.clear()
        stack.add(AppRoute.Chat)
    }
}

@Composable
fun rememberAppNav(): AppNavState = remember { AppNavState() }
