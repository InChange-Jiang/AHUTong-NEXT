package com.ahu.ahutong.next.app.data

import android.content.Context

/** 课表页的界面偏好，对应原工程 ScheduleUiPrefs 的三个开关。 */
class SchedulePrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("schedule_ui", Context.MODE_PRIVATE)

    fun showSaturday(): Boolean = prefs.getBoolean(KEY_SATURDAY, true)
    fun setShowSaturday(value: Boolean) = prefs.edit().putBoolean(KEY_SATURDAY, value).apply()

    fun showSunday(): Boolean = prefs.getBoolean(KEY_SUNDAY, true)
    fun setShowSunday(value: Boolean) = prefs.edit().putBoolean(KEY_SUNDAY, value).apply()

    fun showNowLine(): Boolean = prefs.getBoolean(KEY_NOW_LINE, false)
    fun setShowNowLine(value: Boolean) = prefs.edit().putBoolean(KEY_NOW_LINE, value).apply()

    private companion object {
        const val KEY_SATURDAY = "show_saturday"
        const val KEY_SUNDAY = "show_sunday"
        const val KEY_NOW_LINE = "show_now_timeline"
    }
}
