package com.ahu.ahutong.next.data

interface CampusRepository {
    suspend fun schedule(days: Int = 7): List<CourseOccurrence>
    suspend fun assignments(days: Int = 14): List<Assignment>
    suspend fun grades(term: String? = null): List<Grade>
    suspend fun balance(): Balance
    suspend fun bills(): BillSummary
    suspend fun exams(): List<Exam>
    suspend fun electricity(): Electricity
    suspend fun addCalendarEvent(
        title: String,
        start: String,
        end: String,
        reminderMinutes: Int,
    ): CalendarEvent
}
