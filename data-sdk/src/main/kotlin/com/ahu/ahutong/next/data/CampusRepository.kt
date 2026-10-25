package com.ahu.ahutong.next.data

interface CampusRepository {
    suspend fun schedule(days: Int = 7): List<CourseOccurrence>
    suspend fun assignments(days: Int = 14): List<Assignment>
    suspend fun grades(term: String? = null): List<Grade>
    suspend fun balance(): Balance
    suspend fun bills(): BillSummary
    suspend fun exams(): List<Exam>
    suspend fun electricity(): Electricity

    /**
     * 校园卡充值。bank 只接受 "agricultural" 或 "cmb"。
     * 这是真实扣款操作，调用方必须先取得用户确认。
     */
    suspend fun rechargeCard(amountYuan: String, bank: String): RechargeReceipt

    suspend fun addCalendarEvent(
        title: String,
        start: String,
        end: String,
        reminderMinutes: Int,
    ): CalendarEvent
}
