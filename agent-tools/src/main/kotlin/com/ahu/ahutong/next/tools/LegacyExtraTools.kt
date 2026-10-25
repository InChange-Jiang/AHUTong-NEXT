package com.ahu.ahutong.next.tools

import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.attendance.KqcardAttendanceRepository
import com.ahu.ahutong.data.ProgramCompletionGateway
import com.ahu.ahutong.data.weather.WeatherApi
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetWeatherTool : Tool {
    override val spec = ToolSpec(
        name = "get_weather",
        displayName = "查询天气",
        description = "查询合肥（安徽大学磬苑校区所在地）的实时天气与未来预报。",
        parameters = schema(
            properties = buildJsonObject {
                put("city", stringProp("城市名，默认 合肥"))
            },
        ),
        timeoutMs = 20_000,
        kind = ToolKind.READ,
        source = "天气服务",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val city = arguments.stringOrNull("city") ?: "合肥"
        runCatching {
            val weather = WeatherApi.API.getWeather(
                city = city,
                extended = true,
                indices = false,
                forecast = true,
                hourly = false,
            )
            buildJsonObject {
                put("city", weather.city)
                put("district", weather.district)
                put("weather", weather.weather)
                put("temperature", weather.temperature)
                put("feelsLike", weather.feelsLike)
                put("humidity", weather.humidity)
                put("wind", "${weather.windDirection.orEmpty()} ${weather.windPower.orEmpty()}".trim())
                put("aqi", weather.aqi)
                put("aqiCategory", weather.aqiCategory)
                put("tempMax", weather.tempMax)
                put("tempMin", weather.tempMin)
                put("reportTime", weather.reportTime)
            }
        }.fold(
            onSuccess = { ToolOutput(ok = true, data = it) },
            onFailure = { ToolOutput(ok = false, error = ToolError("weather_failed", it.message ?: "天气查询失败")) },
        )
    }
}

class GetAttendanceTool : Tool {
    override val spec = ToolSpec(
        name = "get_attendance",
        displayName = "查询考勤",
        description = "查询教务考勤记录（正常、迟到、缺勤、请假），默认返回最近一页。",
        parameters = schema(
            properties = buildJsonObject {
                put("page", intProp("页码，默认 1", minimum = 1, maximum = 50))
            },
        ),
        timeoutMs = 45_000,
        kind = ToolKind.READ,
        source = "教务考勤",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(
                ok = false,
                error = ToolError("not_logged_in", "尚未登录安大通账号，无法查询考勤"),
            )
        }
        val page = arguments.intOr("page", 1).coerceIn(1, 50)
        runCatching { KqcardAttendanceRepository.loadPage(status = 0, page = page) }.fold(
            onSuccess = { result ->
                ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("totalCount", result.totalCount)
                        put("records", kotlinx.serialization.json.buildJsonArray {
                            result.records.forEach { record ->
                                add(buildJsonObject {
                                    put("course", record.courseName)
                                    put("teacher", record.teacher)
                                    put("room", "${record.building}${record.room}".trim())
                                    put("date", record.classDate)
                                    put("time", "${record.startTime}-${record.endTime}")
                                    put("punchTime", record.punchTime)
                                    put("status", record.status.label)
                                    put("canAppeal", record.canAppeal)
                                })
                            }
                        })
                    },
                )
            },
            onFailure = {
                ToolOutput(
                    ok = false,
                    error = ToolError("attendance_failed", it.message ?: "考勤查询失败"),
                )
            },
        )
    }
}

class GetProgramCompletionTool : Tool {
    override val spec = ToolSpec(
        name = "get_program_completion",
        displayName = "查询培养方案完成度",
        description = "查询本科培养方案的毕业学分要求、已修学分、各模块完成情况与未通过课程。",
        parameters = schema(properties = buildJsonObject { }),
        timeoutMs = 60_000,
        kind = ToolKind.READ,
        source = "教务系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(
                ok = false,
                error = ToolError("not_logged_in", "尚未登录安大通账号，无法查询培养方案"),
            )
        }
        when (val result = ProgramCompletionGateway.refresh()) {
            is com.ahu.ahutong.core.common.AhuResult.Failure -> ToolOutput(
                ok = false,
                error = ToolError("completion_failed", result.error.toChinese()),
            )
            is com.ahu.ahutong.core.common.AhuResult.Success -> {
                val (program, fetchedAt) = result.value
                val unpassed = program.modules.flatMap { module ->
                    module.courses.filter { it.status != "PASSED" }
                }.take(20)
                ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("programName", program.programName)
                        put("progressPercent", program.progressPercent)
                        put("requiredCredits", program.requiredCredits)
                        put("passedCredits", program.passedCredits)
                        put("takingCredits", program.takingCredits)
                        put("failedCredits", program.failedCredits)
                        put("auditPublished", program.auditPublished)
                        put("fetchedAt", fetchedAt)
                        put("modules", kotlinx.serialization.json.buildJsonArray {
                            program.modules.forEach { module ->
                                add(buildJsonObject {
                                    put("name", module.nameZh)
                                    put("requiredCredits", module.requiredCredits)
                                    put("passedCredits", module.passedCredits)
                                    put("takingCredits", module.takingCredits)
                                    put("result", module.result)
                                })
                            }
                        })
                        put("unpassedCourses", kotlinx.serialization.json.buildJsonArray {
                            unpassed.forEach { course ->
                                add(buildJsonObject {
                                    put("course", course.nameZh)
                                    put("credits", course.credits)
                                    put("status", course.status)
                                    put("grade", course.gradeStr)
                                })
                            }
                        })
                    },
                )
            }
        }
    }
}


class GetNoticesTool : Tool {
    override val spec = ToolSpec(
        name = "get_notices",
        displayName = "查询校园通知",
        description = "查询校园通知。source: all/university(官网)/jwc(教务处)/college(学院)/postgraduate(研究生院)。" +
            "已登录时同步全部来源并返回未读数；未登录时只能查官网。",
        parameters = schema(
            properties = buildJsonObject {
                put("source", stringProp("来源：all/university/jwc/college/postgraduate，默认 all"))
                put("query", stringProp("标题关键词"))
                put("limit", intProp("最多返回条数，默认 20", minimum = 1, maximum = 50))
                put("refresh", boolProp("是否强制重新同步，默认 false"))
            },
        ),
        timeoutMs = 90_000,
        kind = ToolKind.READ,
        source = "校园通知",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val source = when (arguments.stringOrNull("source")) {
            "university", "官网" -> "university"
            "jwc", "教务处" -> "jwc"
            "college", "学院" -> "college"
            "postgraduate", "研究生院" -> "postgraduate"
            else -> null
        }
        val query = arguments.stringOrNull("query")
        val limit = arguments.intOr("limit", 20).coerceIn(1, 50)
        val refresh = arguments.boolOr("refresh", false)
        val accountId = com.ahu.ahutong.data.session.SessionStore.currentUser()
            ?.xh?.takeIf(String::isNotBlank)
        val snapshot = accountId?.let {
            com.ahu.ahutong.data.notice.CampusNoticeRepository.syncForTool(it, refresh)
        }
        if (snapshot == null) {
            if (source != null && source != "university") {
                return@withContext ToolOutput(false, error = ToolError("not_logged_in", "登录后才能查询教务处/学院通知"))
            }
            return@withContext when (val result = LegacyCampusApi.universityNotices()) {
                is com.ahu.ahutong.core.common.AhuResult.Failure ->
                    ToolOutput(false, error = ToolError("notice_failed", result.error.toChinese()))
                is com.ahu.ahutong.core.common.AhuResult.Success -> {
                    val notices = result.value
                        .filter { query == null || it.title.contains(query, ignoreCase = true) }
                        .take(limit)
                    ToolOutput(
                        ok = true,
                        data = buildJsonObject {
                            put("source", "官网")
                            put("unread", 0)
                            put("notices", buildJsonArray {
                                notices.forEach { notice ->
                                    add(noticeJson("university", "安徽大学", notice.title, notice.publishedOn, notice.originalUrl, true))
                                }
                            })
                        },
                    )
                }
            }
        }
        val notices = snapshot.notices
            .filter { source == null || it.sourceId == source }
            .filter { query == null || it.title.contains(query, ignoreCase = true) }
            .sortedByDescending { it.publishedOn }
            .take(limit)
        ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("source", source ?: "all")
                put("unread", snapshot.unreadCount)
                put("notices", buildJsonArray {
                    notices.forEach { notice ->
                        add(
                            noticeJson(
                                notice.sourceId,
                                sourceLabel(notice.sourceId),
                                notice.title,
                                notice.publishedOn,
                                notice.originalUrl,
                                notice.read,
                            ),
                        )
                    }
                })
            },
        )
    }

    private fun noticeJson(
        sourceId: String,
        sourceName: String,
        title: String,
        publishedOn: String,
        url: String,
        read: Boolean,
    ): JsonObject = buildJsonObject {
        put("sourceId", sourceId)
        put("source", sourceName)
        put("title", title)
        put("publishedOn", publishedOn)
        put("url", url)
        put("read", read)
    }

    private fun sourceLabel(sourceId: String): String = when (sourceId) {
        "university" -> "安徽大学"
        "jwc" -> "教务处"
        "college" -> "学院"
        "postgraduate" -> "研究生院"
        else -> sourceId
    }
}


class GetFreeRoomsTool : Tool {
    override val spec = ToolSpec(
        name = "get_free_rooms",
        displayName = "查询空闲教室",
        description = "查询指定日期（默认今天）某校区的空闲教室。campus: 1=磬苑校区，2=龙河校区；units 为节次，默认 1-13；building 可按教学楼筛选。",
        parameters = schema(
            properties = buildJsonObject {
                put("campus", intProp("校区：1=磬苑，2=龙河；默认 1", minimum = 1, maximum = 2))
                put("date", stringProp("日期 yyyy-MM-dd，默认今天"))
                put("units", stringProp("节次，逗号分隔，例如 1,2,3；默认全部 1-13"))
                put("building", stringProp("教学楼名称，例如 博学北楼；不传返回全部"))
            },
        ),
        timeoutMs = 90_000,
        kind = ToolKind.READ,
        source = "教务系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(
                ok = false,
                error = ToolError("not_logged_in", "尚未登录安大通账号，无法查询空闲教室"),
            )
        }
        val campus = arguments.intOr("campus", 1).coerceIn(1, 2)
        val date = arguments.stringOrNull("date") ?: java.time.LocalDate.now().toString()
        val units = arguments.stringOrNull("units")
            ?.split(',', '，')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..13 }
            ?.map(Int::toString)
            ?.takeIf { it.isNotEmpty() }
            ?: (1..13).map(Int::toString)
        val building = arguments.stringOrNull("building")
        when (val result = LegacyCampusApi.freeRooms(campus, date, units)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure -> ToolOutput(
                ok = false,
                error = ToolError("free_rooms_failed", result.error.toChinese()),
            )
            is com.ahu.ahutong.core.common.AhuResult.Success -> {
                val allRooms = result.value
                val buildings = allRooms.map { it.substringBefore(' ') }.distinct().sorted()
                val rooms = if (building == null) {
                    allRooms
                } else {
                    allRooms.filter { it.substringBefore(' ').contains(building, ignoreCase = true) }
                }
                ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("date", date)
                        put("campus", campus)
                        put("building", building)
                        put("buildings", kotlinx.serialization.json.buildJsonArray {
                            buildings.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        })
                        put("count", rooms.size)
                        put("totalCount", allRooms.size)
                        put("rooms", kotlinx.serialization.json.buildJsonArray {
                            rooms.take(60).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        })
                    },
                )
            }
        }
    }
}
