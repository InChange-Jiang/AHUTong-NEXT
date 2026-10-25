package com.ahu.ahutong.next.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.MainActivity
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.NextCampusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object CourseReminders {
    private const val PREFS = "next_course_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_ELECTRICITY = "electricity_enabled"
    private const val KEY_THRESHOLD = "electricity_threshold"
    private const val KEY_CODES = "codes"
    const val CHANNEL_ID = "course_reminder"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun minutesBefore(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MINUTES, 10)

    fun electricityEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ELECTRICITY, false)

    fun electricityThreshold(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THRESHOLD, 7)

    fun save(
        context: Context,
        enabled: Boolean,
        minutes: Int,
        electricity: Boolean,
        threshold: Int,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_MINUTES, minutes)
            .putBoolean(KEY_ELECTRICITY, electricity)
            .putInt(KEY_THRESHOLD, threshold)
            .apply()
    }

    fun schedule(context: Context, repository: NextCampusRepository = NextCampusRepository()) {
        val app = context.applicationContext
        cancelAll(app)
        if (!isEnabled(app) || !LegacyCampusApi.isLoggedIn()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repository.schedule(7) }.onSuccess { courses ->
                val alarm = app.getSystemService(AlarmManager::class.java)
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val codes = mutableSetOf<Int>()
                val minutes = minutesBefore(app)
                courses.forEach { course ->
                    runCatching {
                        val start = LocalDateTime.of(
                            LocalDate.parse(course.date),
                            LocalTime.parse(course.startTime.padStart(5, '0')),
                        )
                        val trigger = start.minusMinutes(minutes.toLong())
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        if (trigger <= System.currentTimeMillis()) return@forEach
                        val code = (course.date + course.startTime + course.name).hashCode()
                        codes += code
                        val intent = Intent(app, CourseReminderReceiver::class.java).apply {
                            putExtra("title", course.name)
                            putExtra("time", "${course.startTime}-${course.endTime}")
                            putExtra("location", course.location)
                        }
                        val pending = PendingIntent.getBroadcast(
                            app,
                            code,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                    }
                }
                prefs.edit().putStringSet(KEY_CODES, codes.map(Int::toString).toSet()).apply()
            }
        }
    }

    private fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        val alarm = context.getSystemService(AlarmManager::class.java)
        codes.forEach { raw ->
            raw.toIntOrNull()?.let { code ->
                alarm.cancel(
                    PendingIntent.getBroadcast(
                        context,
                        code,
                        Intent(context, CourseReminderReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }
        prefs.edit().remove(KEY_CODES).apply()
    }
}

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val location = intent.getStringExtra("location").orEmpty()
        if (title.isBlank()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CourseReminders.CHANNEL_ID,
                    "上课提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CourseReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_iconpark_bell)
            .setContentTitle("下一节：$title")
            .setContentText(listOf(time, location).filter { it.isNotBlank() }.joinToString(" · "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(title.hashCode(), notification) }
    }
}

class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching {
                com.ahu.ahutong.legacy.LegacyCampusApi.init(context)
                com.ahu.ahutong.legacy.LegacyDataApi.init(context)
            }
            CourseReminders.schedule(context)
        }
    }
}
