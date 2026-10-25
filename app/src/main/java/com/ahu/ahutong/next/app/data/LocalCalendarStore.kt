package com.ahu.ahutong.next.app.data

import com.ahu.ahutong.next.data.CalendarEvent
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** 本地日程的持久化存储；工具写入后重启仍然存在。 */
class LocalCalendarStore(context: android.content.Context) {
    private val file = File(context.filesDir, "local_calendar.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<CalendarEvent> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(
            ListSerializer(CalendarEvent.serializer()),
            file.readText(),
        )
    }.getOrDefault(emptyList())

    fun add(event: CalendarEvent): List<CalendarEvent> {
        val next = (load() + event).sortedBy { it.start }
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(CalendarEvent.serializer()), next))
        }
        return next
    }

    fun delete(id: String): List<CalendarEvent> {
        val next = load().filterNot { it.id == id }
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(CalendarEvent.serializer()), next))
        }
        return next
    }
}
