package com.ahu.ahutong.next.data

import kotlinx.serialization.Serializable

@Serializable
data class CourseOccurrence(
    val date: String,
    val weekday: Int,
    val name: String,
    val teacher: String,
    val location: String,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class Assignment(
    val course: String,
    val title: String,
    val dueAt: String,
    val status: String,
    /** 作业开放/截止时间，格式 yyyy-MM-dd HH:mm；日历按这段区间铺色块。 */
    val startAt: String? = null,
    val endAt: String? = null,
)

@Serializable
data class Grade(
    val course: String,
    val credit: Double,
    val score: Double,
    val gpa: Double,
    val term: String,
    /** 百分制以外的成绩原文（优 / 良 / 合格等），为空时显示 [score]。 */
    val scoreText: String? = null,
    /** 教务学期 ID，用于对齐绩点排名接口里的学期汇总。 */
    val semesterId: Int = 0,
)

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val reminderMinutes: Int,
)

@Serializable
data class Balance(
    val yuan: Double,
    val cardNo: String,
    val updatedAt: String,
)

@Serializable
data class BillItem(
    val time: String,
    val place: String,
    val amount: Double,
    val kind: String,
)

@Serializable
data class BillSummary(
    val monthSpend: Double,
    val monthIncome: Double,
    val topPlace: String,
    val items: List<BillItem>,
)

@Serializable
data class RechargeReceipt(
    val orderId: String,
    val amountYuan: Double,
    val channel: String,
    val target: String,
    val message: String,
)

@Serializable
data class Exam(
    val course: String,
    val time: String,
    val location: String,
    val seat: String,
)

@Serializable
data class Electricity(
    val room: String,
    val balanceKwh: Double,
    val dailyUsage: List<Double>,
    val estimatedDays: Int,
)
