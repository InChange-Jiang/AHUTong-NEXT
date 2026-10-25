package com.ahu.ahutong.next.app.data

import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.data.CourseOccurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 学习通作业的贪心排期：按截止时间从早到晚，为未完成作业找最早的空闲时段，
 * 避开课程和已有日程，写进本地日历。一次只排未来 21 天内的作业。
 */
object AssignmentPlanner {
    private val PARSE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val SLOT = 60L
    private const val HORIZON_DAYS = 21L

    data class Result(
        val scheduled: List<CalendarEvent>,
        val skipped: Int,
    )

    suspend fun plan(
        repository: NextCampusRepository,
        assignments: List<Assignment>,
    ): Result {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val today = now.toLocalDate()
        val courses = runCatching { repository.schedule(HORIZON_DAYS.toInt() + 1) }.getOrDefault(emptyList())
        val existing = repository.calendarEvents()
        val busy = mutableMapOf<LocalDate, MutableList<Pair<LocalDateTime, LocalDateTime>>>()

        fun addBusy(date: LocalDate, start: LocalDateTime, end: LocalDateTime) {
            busy.getOrPut(date) { mutableListOf() }.add(start to end)
        }

        courses.forEach { course ->
            runCatching {
                val date = LocalDate.parse(course.date)
                addBusy(
                    date,
                    LocalDateTime.of(date, LocalTime.parse(course.startTime.padStart(5, '0'))),
                    LocalDateTime.of(date, LocalTime.parse(course.endTime.padStart(5, '0'))),
                )
            }
        }
        existing.forEach { event ->
            runCatching {
                val start = LocalDateTime.parse(event.start.take(16), PARSE)
                val end = LocalDateTime.parse(event.end.take(16), PARSE)
                addBusy(start.toLocalDate(), start, end)
            }
        }

        val pending = assignments
            .filter { !it.status.contains("已完成") }
            .mapNotNull { work ->
                runCatching { work to LocalDateTime.parse(work.dueAt.take(16), PARSE) }.getOrNull()
            }
            .filter { (_, due) -> due.isAfter(now.plusHours(2)) }
            .sortedBy { it.second }
            .filter { !existing.any { event -> event.title == "作业：${it.first.title}" } }

        val scheduled = mutableListOf<CalendarEvent>()
        val plannedPerDay = mutableMapOf<LocalDate, Int>()
        var skipped = 0
        pending.forEach { (work, due) ->
            val latest = due.minusHours(2)
            var slot: Pair<LocalDateTime, LocalDateTime>? = null
            var date = today
            while (slot == null && !date.isAfter(min(due.toLocalDate(), today.plusDays(HORIZON_DAYS)))) {
                if ((plannedPerDay[date] ?: 0) >= 2) {
                    date = date.plusDays(1)
                    continue
                }
                val weekend = date.dayOfWeek.value >= 6
                val windows = if (weekend) {
                    listOf(LocalTime.of(9, 0) to LocalTime.of(21, 0))
                } else {
                    listOf(LocalTime.of(19, 0) to LocalTime.of(22, 0))
                }
                for ((from, to) in windows) {
                    var candidate = LocalDateTime.of(date, from)
                    if (candidate.isBefore(now.plusHours(1))) {
                        candidate = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                    }
                    while (!candidate.plusMinutes(SLOT).isAfter(LocalDateTime.of(date, to))) {
                        val end = candidate.plusMinutes(SLOT)
                        if (end.isAfter(latest)) break
                        val conflict = busy[date].orEmpty().any { (busyStart, busyEnd) ->
                            candidate.isBefore(busyEnd) && end.isAfter(busyStart)
                        }
                        if (!conflict) {
                            slot = candidate to end
                            break
                        }
                        candidate = candidate.plusMinutes(30)
                    }
                    if (slot != null) break
                }
                date = date.plusDays(1)
            }
            val picked = slot
            if (picked == null) {
                skipped++
            } else {
                val event = repository.addCalendarEvent(
                    title = "作业：${work.title}",
                    start = picked.first.format(PARSE),
                    end = picked.second.format(PARSE),
                    reminderMinutes = 15,
                )
                addBusy(picked.first.toLocalDate(), picked.first, picked.second)
                plannedPerDay[picked.first.toLocalDate()] =
                    (plannedPerDay[picked.first.toLocalDate()] ?: 0) + 1
                scheduled += event
            }
        }
        return Result(scheduled, skipped)
    }

    private fun min(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) a else b
}
