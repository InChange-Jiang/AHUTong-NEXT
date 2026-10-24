package com.ahu.ahutong.next.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 首版地基用的本地演示数据。后续用旧工程的数据层替换实现，接口不变。
 */
class MockCampusRepository : CampusRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val events = mutableListOf<CalendarEvent>()

    override suspend fun schedule(days: Int): List<CourseOccurrence> {
        val today = LocalDate.now()
        val plan = mapOf(
            DayOfWeek.MONDAY to listOf(
                Course("高等数学", "李老师", "博学南楼 A201", "08:00", "09:40"),
                Course("大学物理", "陈老师", "博学北楼 B105", "10:00", "11:40"),
                Course("程序设计基础", "王老师", "计算机楼 302", "14:00", "15:40"),
            ),
            DayOfWeek.TUESDAY to listOf(
                Course("大学英语", "刘老师", "外语楼 204", "08:00", "09:40"),
                Course("线性代数", "赵老师", "博学南楼 A301", "10:00", "11:40"),
                Course("体育", "周老师", "东区操场", "16:00", "17:40"),
            ),
            DayOfWeek.WEDNESDAY to listOf(
                Course("高等数学", "李老师", "博学南楼 A201", "08:00", "09:40"),
                Course("数据结构", "孙老师", "计算机楼 401", "10:00", "11:40"),
                Course("大学物理实验", "陈老师", "实验楼 205", "14:00", "16:30"),
            ),
            DayOfWeek.THURSDAY to listOf(
                Course("大学英语", "刘老师", "外语楼 204", "08:00", "09:40"),
                Course("程序设计基础", "王老师", "计算机楼 302", "10:00", "11:40"),
                Course("形势与政策", "郑老师", "博学南楼 A102", "14:00", "15:40"),
            ),
            DayOfWeek.FRIDAY to listOf(
                Course("线性代数", "赵老师", "博学南楼 A301", "08:00", "09:40"),
                Course("数据结构", "孙老师", "计算机楼 401", "10:00", "11:40"),
                Course("体育", "周老师", "东区操场", "16:00", "17:40"),
            ),
        )

        return (0 until days.coerceIn(1, 30)).flatMap { offset ->
            val date = today.plusDays(offset.toLong())
            plan[date.dayOfWeek].orEmpty().map { course ->
                CourseOccurrence(
                    date = date.format(dateFormatter),
                    weekday = date.dayOfWeek.value,
                    name = course.name,
                    teacher = course.teacher,
                    location = course.location,
                    startTime = course.startTime,
                    endTime = course.endTime,
                )
            }
        }.sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    override suspend fun assignments(days: Int): List<Assignment> {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        return listOf(
            Assignment(
                course = "数据结构",
                title = "第 5 章 树与二叉树习题",
                dueAt = now.plusDays(1).withHour(23).withMinute(59).format(dateTimeFormatter),
                status = "未提交",
            ),
            Assignment(
                course = "大学物理",
                title = "实验报告：杨氏双缝干涉",
                dueAt = now.plusDays(3).withHour(18).withMinute(0).format(dateTimeFormatter),
                status = "未提交",
            ),
            Assignment(
                course = "程序设计基础",
                title = "课程设计选题说明",
                dueAt = now.plusDays(5).withHour(12).withMinute(0).format(dateTimeFormatter),
                status = "未提交",
            ),
            Assignment(
                course = "大学英语",
                title = "Unit 4 写作练习",
                dueAt = now.minusDays(1).withHour(23).withMinute(59).format(dateTimeFormatter),
                status = "已完成",
            ),
        ).sortedWith(compareBy({ it.status == "已完成" }, { it.dueAt }))
    }

    override suspend fun grades(term: String?): List<Grade> {
        val selected = term ?: "2026-2027 学年第一学期"
        return listOf(
            Grade("高等数学", 6.0, 92.0, 4.2, selected),
            Grade("大学物理", 4.0, 88.0, 3.8, selected),
            Grade("程序设计基础", 3.0, 95.0, 4.5, selected),
            Grade("大学英语", 2.0, 84.0, 3.3, selected),
            Grade("线性代数", 3.0, 90.0, 4.0, selected),
        )
    }

    override suspend fun addCalendarEvent(
        title: String,
        start: String,
        end: String,
        reminderMinutes: Int,
    ): CalendarEvent {
        val event = CalendarEvent(
            id = UUID.randomUUID().toString(),
            title = title,
            start = start,
            end = end,
            reminderMinutes = reminderMinutes,
        )
        events += event
        return event
    }

    fun eventsSnapshot(): List<CalendarEvent> = events.toList()

    private data class Course(
        val name: String,
        val teacher: String,
        val location: String,
        val startTime: String,
        val endTime: String,
    )
}
