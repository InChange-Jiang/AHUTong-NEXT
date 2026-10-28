package com.ahu.ahutong.next.ui.nav

import com.ahu.ahutong.next.R

enum class TopLevelTab(
    val label: String,
    val icon: Int,
) {
    CHAT("对话", R.drawable.ic_iconpark_message),
    SCHEDULE("课表", R.drawable.ic_iconpark_calendar),
    CALENDAR("日程", R.drawable.ic_iconpark_clock),
}
