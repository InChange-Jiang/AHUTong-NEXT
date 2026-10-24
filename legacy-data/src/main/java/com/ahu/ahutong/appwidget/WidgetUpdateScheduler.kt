package com.ahu.ahutong.appwidget

import android.content.Context

/**
 * No-op stub of the old widget scheduler (it lived in `:background`).
 *
 * `RepositoryScheduleHolidaySource` only asks the widget to redraw after holidays change.
 * Widgets are UI and are not part of `:legacy-data`, so the call becomes a no-op here.
 */
object WidgetUpdateScheduler {
    @Suppress("UNUSED_PARAMETER")
    fun renderCached(context: Context) = Unit
}
