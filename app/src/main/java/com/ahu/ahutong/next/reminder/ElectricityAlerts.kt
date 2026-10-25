package com.ahu.ahutong.next.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.MainActivity
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.NextCampusRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 电费提醒与旧版一致：前台进入时检查一次，同一房间每天最多提醒一次。 */
object ElectricityAlerts {
    private const val PREFS = "next_electricity_alerts"
    private const val KEY_LAST = "last_notified"
    const val CHANNEL_ID = "electricity_alert"

    fun check(context: Context, repository: NextCampusRepository = NextCampusRepository()) {
        if (!CourseReminders.electricityEnabled(context) || !LegacyCampusApi.isLoggedIn()) return
        if (repository.electricityRoomLabel() == null) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val electricity = runCatching { repository.electricity() }.getOrNull() ?: return@launch
            val threshold = CourseReminders.electricityThreshold(context)
            if (electricity.estimatedDays > threshold) return@launch
            val today = LocalDate.now().toString()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST, null) == "$today:${electricity.room}") return@launch
            prefs.edit().putString(KEY_LAST, "$today:${electricity.room}").apply()
            post(context, electricity.room, electricity.balanceKwh, electricity.estimatedDays)
        }
    }

    private fun post(context: Context, room: String, kwh: Double, days: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "电费提醒", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_iconpark_electricity)
            .setContentTitle("宿舍电费可能不足")
            .setContentText("$room 剩余 ${"%.1f".format(kwh)} 度，预计可用 $days 天")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(room.hashCode(), notification) }
    }
}
