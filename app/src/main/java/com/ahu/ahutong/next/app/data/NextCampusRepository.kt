package com.ahu.ahutong.next.app.data

import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.recharge.ElectricityUsagePredictor
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.Balance
import com.ahu.ahutong.next.data.BillItem
import com.ahu.ahutong.next.data.BillSummary
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.data.CampusRepository
import com.ahu.ahutong.next.data.CourseOccurrence
import com.ahu.ahutong.next.data.Electricity
import com.ahu.ahutong.next.data.Exam
import com.ahu.ahutong.next.data.Grade
import com.ahu.ahutong.next.data.MockCampusRepository
import com.ahu.ahutong.core.common.AhuResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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

    // 未登录时继续用演示数据；已登录时查询真实一卡通与电费，失败直接报错，不冒充真实结果。
    override suspend fun balance(): Balance {
        if (!real) return demo.balance()
        val card = LegacyCampusApi.cardMoney().orThrow()
        val cardNo = when (val number = LegacyCampusApi.cardNumber()) {
            is AhuResult.Success -> number.value
            is AhuResult.Failure -> "校园卡"
        }
        return Balance(
            yuan = card.balance ?: 0.0,
            cardNo = cardNo,
            updatedAt = LocalDateTime.now().format(TIME_FORMAT),
        )
    }

    override suspend fun bills(): BillSummary {
        if (!real) return demo.bills()
        val now = LocalDateTime.now()
        val timeFrom = now.toLocalDate().withDayOfMonth(1).atStartOfDay().format(TIME_FORMAT)
        val timeTo = now.format(TIME_FORMAT)
        val summary = LegacyCampusApi.billSummary(timeFrom, timeTo).orThrow()
        val records = LegacyCampusApi.billPage(1, BILL_PAGE_SIZE, timeFrom, timeTo)
            .orThrow()
            .records
            .orEmpty()
            .sortedByDescending { it.effectdateStr.orEmpty() }
        val items = records.map { record ->
            val expense = record.isExpense
            val yuan = record.tranamt / 100.0
            BillItem(
                time = record.effectdateStr ?: record.jndatetimeStr.orEmpty(),
                place = record.displayPlace(),
                amount = if (expense) -yuan else yuan,
                kind = if (expense) "消费" else "充值",
            )
        }
        val topPlace = records
            .filter { it.isExpense }
            .groupingBy { it.displayPlace() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "暂无"
        return BillSummary(
            monthSpend = (summary.expenses ?: 0L) / 100.0,
            monthIncome = (summary.income ?: 0L) / 100.0,
            topPlace = topPlace,
            items = items,
        )
    }

    override suspend fun electricity(): Electricity {
        if (!real) return demo.electricity()
        val selection = LegacyCampusApi.savedElectricityRoom()?.takeIf { it.room != null }
            ?: throw IllegalStateException("还没有设置宿舍电费房间，请先到「设置 → 宿舍电费」里选一次")
        val controller = LegacyCampusApi.electricityController()
        val asOf = LocalDate.now()
        val snapshot = LegacyCampusApi.electricitySnapshot(controller, selection, asOf).orThrow()
        val estimatedDays = runCatching {
            ElectricityUsagePredictor.predict(snapshot, 1).daysRemaining?.roundToInt() ?: 0
        }.getOrDefault(0)
        val byDate = snapshot.dailyUsage
            .groupingBy { it.date }
            .fold(0.0) { total, reading -> total + reading.kwh }
        val lastSevenDays = (7 downTo 1).map { byDate[asOf.minusDays(it.toLong())] ?: 0.0 }
        return Electricity(
            room = listOfNotNull(
                selection.campus?.name,
                selection.building?.name,
                selection.floor?.name,
                selection.room?.name,
            ).joinToString(" "),
            balanceKwh = snapshot.remainingKwh,
            dailyUsage = lastSevenDays,
            estimatedDays = estimatedDays,
        )
    }

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

    private companion object {
        const val BILL_PAGE_SIZE = 50
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

private fun TurnoverRecord.displayPlace(): String =
    toMerchant?.takeIf { it.isNotBlank() }
        ?: resume?.takeIf { it.isNotBlank() }
        ?: consumeTypeName?.takeIf { it.isNotBlank() }
        ?: turnoverType?.takeIf { it.isNotBlank() }
        ?: "校园卡"
