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
)

@Serializable
data class Grade(
    val course: String,
    val credit: Double,
    val score: Double,
    val gpa: Double,
    val term: String,
)

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val reminderMinutes: Int,
)
