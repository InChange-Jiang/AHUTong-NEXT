package com.ahu.ahutong.next.app.data

import android.content.Context
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 自定义课程的一个上课时间；同一门课可以有多个时间，周次可独立选择。 */
@Serializable
data class CustomCourseSlot(
    val weekday: Int = 1,
    val startSection: Int = 1,
    val endSection: Int = 2,
    val useCustomTime: Boolean = false,
    val startMinutes: Int = 8 * 60,
    val endMinutes: Int = 9 * 60 + 35,
    val weeks: List<Int> = (1..20).toList(),
)

/** 用户手动添加的课程，按账号隔离；只在本机保存，不影响教务课表。 */
@Serializable
data class CustomCourseRecord(
    val id: String = UUID.randomUUID().toString(),
    val semesterId: Int = 0,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val note: String = "",
    val colorArgb: Int? = null,
    val slots: List<CustomCourseSlot> = emptyList(),
)

class CustomCourseStore(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(accountId: String): List<CustomCourseRecord> = runCatching {
        val file = fileOf(accountId)
        if (!file.exists()) emptyList() else json.decodeFromString(
            ListSerializer(CustomCourseRecord.serializer()),
            file.readText(),
        )
    }.getOrDefault(emptyList())

    fun save(accountId: String, items: List<CustomCourseRecord>) {
        runCatching {
            fileOf(accountId).writeText(
                json.encodeToString(ListSerializer(CustomCourseRecord.serializer()), items),
            )
        }
    }

    fun add(accountId: String, record: CustomCourseRecord): List<CustomCourseRecord> {
        val next = load(accountId).filterNot { it.id == record.id } + record
        save(accountId, next)
        return next
    }

    fun update(accountId: String, record: CustomCourseRecord): List<CustomCourseRecord> {
        val next = load(accountId).map { if (it.id == record.id) record else it }
        save(accountId, next)
        return next
    }

    fun delete(accountId: String, id: String): List<CustomCourseRecord> {
        val next = load(accountId).filterNot { it.id == id }
        save(accountId, next)
        return next
    }

    private fun fileOf(accountId: String): File =
        File(dir, "custom_courses_${accountId.ifBlank { "anon" }}.json")
}

fun CustomCourseRecord.toCourses(): List<Course> = slots.mapNotNull { slot ->
    val weeks = slot.weeks.distinct().sorted().filter { it in 1..20 }
    if (weeks.isEmpty()) return@mapNotNull null
    val span = slot.resolvedSpan()
    Course().apply {
        name = this@toCourses.name
        teacher = this@toCourses.teacher
        location = this@toCourses.location
        extra = this@toCourses.note
        courseId = "custom-${this@toCourses.id}-${slot.weekday}-${span.first}"
        setWeekday(slot.weekday.toString())
        setStartTime(span.first.toString())
        setLength((span.last - span.first + 1).toString())
        setStartWeek(weeks.first().toString())
        setEndWeek(weeks.last().toString())
        setWeekIndexes(weeks)
        clockRange = slot.displayClockRange()
    }
}

/** 与旧工程一致：自定义时间按最近的节次吸附，网格仍按节次排版。 */
private fun CustomCourseSlot.resolvedSpan(): IntRange {
    if (!useCustomTime) {
        val first = startSection.coerceIn(1, 13)
        val last = endSection.coerceIn(first, 13)
        return first..last
    }
    val safeStart = startMinutes.coerceIn(0, 24 * 60 - 1)
    val safeEnd = endMinutes.coerceIn(0, 24 * 60 - 1)
    val start = ScheduleSectionTimes.timetable.entries
        .minWithOrNull(
            compareBy<Map.Entry<Int, String>> {
                kotlin.math.abs(it.value.substringBefore('-').clockMinutes() - safeStart)
            }.thenBy { it.key },
        )?.key ?: 1
    val end = ScheduleSectionTimes.timetable.entries
        .minWithOrNull(
            compareBy<Map.Entry<Int, String>> {
                kotlin.math.abs(it.value.substringAfter('-').clockMinutes() - safeEnd)
            }.thenBy { it.key },
        )?.key ?: 13
    return if (end < start) start..start else start..end
}

private fun CustomCourseSlot.displayClockRange(): String? {
    if (!useCustomTime) return null
    if (startMinutes !in 0..(24 * 60 - 1) || endMinutes !in 0..(24 * 60 - 1)) return null
    if (endMinutes <= startMinutes) return null
    return "${startMinutes.clockText()}-${endMinutes.clockText()}"
}

fun Int.clockText(): String = "%02d:%02d".format(this / 60, this % 60)

private fun String.clockMinutes(): Int {
    val start = substringBefore(':').toIntOrNull() ?: return 0
    val end = substringAfter(':').toIntOrNull() ?: return 0
    return start * 60 + end
}

fun CustomCourseSlot.spanForDisplay(): IntRange = resolvedSpan()

fun CustomCourseSlot.clockRangeForDisplay(): String? = displayClockRange()
