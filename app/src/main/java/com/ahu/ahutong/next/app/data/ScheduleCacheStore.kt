package com.ahu.ahutong.next.app.data

import android.content.Context
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleSemester
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedSemester(
    val id: Int,
    val name: String,
    val startDate: String? = null,
    val isCurrent: Boolean = false,
)

@Serializable
data class CachedCourse(
    val weekday: Int,
    val startWeek: Int,
    val endWeek: Int,
    val startTime: Int,
    val length: Int,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val clockRange: String? = null,
    val courseId: String = "",
    val weeks: List<Int> = emptyList(),
)

@Serializable
data class ScheduleCache(
    val semesters: List<CachedSemester> = emptyList(),
    val coursesBySemester: Map<String, List<CachedCourse>> = emptyMap(),
    val currentWeek: Int = 1,
    val updatedAt: Long = 0L,
)

/** 按账号缓存学期列表与整学期课程；进入课表先读它，只有主动刷新才回源教务。 */
class ScheduleCacheStore(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(accountId: String): ScheduleCache? = runCatching {
        val file = fileOf(accountId)
        if (!file.exists()) null else json.decodeFromString(ScheduleCache.serializer(), file.readText())
    }.getOrNull()

    fun save(accountId: String, cache: ScheduleCache) {
        runCatching {
            fileOf(accountId).writeText(json.encodeToString(ScheduleCache.serializer(), cache))
        }
    }

    private fun fileOf(accountId: String): File =
        File(dir, "schedule_cache_${accountId.ifBlank { "anon" }}.json")
}

fun ScheduleSemester.toCached(): CachedSemester = CachedSemester(
    id = id,
    name = name,
    startDate = startDate,
    isCurrent = isCurrent,
)

fun CachedSemester.toSemester(): ScheduleSemester = ScheduleSemester(
    id = id,
    name = name,
    startDate = startDate,
    isCurrent = isCurrent,
)

fun Course.toCached(): CachedCourse = CachedCourse(
    weekday = weekday,
    startWeek = startWeek,
    endWeek = endWeek,
    startTime = startTime,
    length = length,
    name = name.orEmpty(),
    teacher = teacher.orEmpty(),
    location = location.orEmpty(),
    clockRange = clockRange,
    courseId = courseId.orEmpty(),
    weeks = weekIndexes.toList(),
)

fun CachedCourse.toCourse(): Course = Course().apply {
    name = this@toCourse.name
    teacher = this@toCourse.teacher
    location = this@toCourse.location
    clockRange = this@toCourse.clockRange
    courseId = this@toCourse.courseId
    setWeekday(this@toCourse.weekday.toString())
    setStartWeek(this@toCourse.startWeek.toString())
    setEndWeek(this@toCourse.endWeek.toString())
    setStartTime(this@toCourse.startTime.toString())
    setLength(this@toCourse.length.toString())
    setWeekIndexes(this@toCourse.weeks)
}
