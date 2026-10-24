package com.ahu.ahutong.next.app.data

import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.Balance
import com.ahu.ahutong.next.data.BillSummary
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.data.CampusRepository
import com.ahu.ahutong.next.data.CourseOccurrence
import com.ahu.ahutong.next.data.Electricity
import com.ahu.ahutong.next.data.Exam
import com.ahu.ahutong.next.data.Grade
import com.ahu.ahutong.next.data.MockCampusRepository
import com.ahu.ahutong.core.common.AhuResult

/**
 * 已登录时走旧工程真实数据层，未登录时退回演示数据。
 * 真实请求失败不会静默换成演示数据——宁可告诉用户“登录失效”，也不能把假数据当真的给。
 */
class NextCampusRepository(
    private val demo: MockCampusRepository = MockCampusRepository(),
) : CampusRepository {

    private val real: Boolean get() = LegacyCampusApi.isLoggedIn()

    override suspend fun schedule(days: Int): List<CourseOccurrence> {
        if (!real) return demo.schedule(days)
        val courses = LegacyCampusApi.scheduleOccurrences(days).orThrow()
        return courses.map {
            CourseOccurrence(
                date = it.date,
                weekday = it.weekday,
                name = it.name,
                teacher = it.teacher,
                location = it.location,
                startTime = it.startTime,
                endTime = it.endTime,
            )
        }
    }

    override suspend fun grades(term: String?): List<Grade> {
        if (!real) return demo.grades(term)
        val grade = LegacyCampusApi.grades().orThrow()
        val terms = grade.termGradeList.orEmpty()
            .filter { term == null || it.schoolYear.contains(term) || it.term == term }
        if (terms.isEmpty() && term == null && grade.termGradeList.isNullOrEmpty()) {
            throw IllegalStateException("教务系统暂时没有返回成绩")
        }
        return terms.flatMap { termBean ->
            val label = "${termBean.schoolYear} 第${termBean.term}学期"
            termBean.gradeList.orEmpty().map { item ->
                val scoreText = item.grade?.takeIf { it.isNotBlank() }
                Grade(
                    course = item.course.orEmpty(),
                    credit = item.credit?.toDoubleOrNull() ?: 0.0,
                    score = scoreText?.toDoubleOrNull() ?: 0.0,
                    gpa = item.gradePoint?.toDoubleOrNull() ?: 0.0,
                    term = label,
                    scoreText = scoreText,
                )
            }
        }
    }

    override suspend fun exams(): List<Exam> {
        if (!real) return demo.exams()
        return LegacyCampusApi.exams().orThrow().map {
            Exam(
                course = it.course.orEmpty(),
                time = it.time.orEmpty(),
                location = it.location.orEmpty(),
                seat = it.seatNum.orEmpty(),
            )
        }
    }

    // Money-related endpoints are intentionally left on demo data: NEXT has no payment path,
    // and the acceptance run must not touch recharge/billing systems.
    override suspend fun balance(): Balance = demo.balance()

    override suspend fun bills(): BillSummary = demo.bills()

    override suspend fun electricity(): Electricity = demo.electricity()

    override suspend fun assignments(days: Int): List<Assignment> {
        if (!LegacyCampusApi.hasChaoxingSession()) return demo.assignments(days)
        return LegacyCampusApi.assignments().orThrow().map { work ->
            Assignment(
                course = work.courseName,
                title = work.title,
                dueAt = work.endTs?.let { epoch ->
                    java.time.Instant.ofEpochMilli(epoch)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                } ?: work.rawEnd,
                status = if (work.isDone) "已完成" else "未提交",
            )
        }
    }

    override suspend fun addCalendarEvent(
        title: String,
        start: String,
        end: String,
        reminderMinutes: Int,
    ): CalendarEvent = demo.addCalendarEvent(title, start, end, reminderMinutes)

    private fun <T> AhuResult<T>.orThrow(): T = when (this) {
        is AhuResult.Success -> value
        is AhuResult.Failure -> throw IllegalStateException(error.toChinese())
    }
}
