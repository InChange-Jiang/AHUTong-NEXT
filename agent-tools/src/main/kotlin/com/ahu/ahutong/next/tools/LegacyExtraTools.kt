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


class GetLostFoundTool : Tool {
    override val spec = ToolSpec(
        name = "get_lost_found",
        displayName = "查询失物招领",
        description = "查询校园失物招领的最新信息，返回标题、类型、校区与发布时间。只读。",
        parameters = schema(
            properties = buildJsonObject {
                put("page", intProp("页码，默认 1", minimum = 1, maximum = 20))
                put("page_size", intProp("每页条数，默认 20", minimum = 1, maximum = 50))
            },
        ),
        timeoutMs = 30_000,
        kind = ToolKind.READ,
        source = "失物招领",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(
                ok = false,
                error = ToolError("not_logged_in", "尚未登录安大通账号，无法查询失物招领"),
            )
        }
        val page = arguments.intOr("page", 1).coerceIn(1, 20)
        val size = arguments.intOr("page_size", 20).coerceIn(1, 50)
        when (val result = AHURepository.getLostFoundList(page, size, state = 1)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure -> ToolOutput(
                ok = false,
                error = ToolError("lost_found_failed", result.error.toChinese()),
            )
            is com.ahu.ahutong.core.common.AhuResult.Success -> {
                val pageData = result.value.data
                ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("total", pageData.total)
                        put("items", kotlinx.serialization.json.buildJsonArray {
                            pageData.list.forEach { item ->
                                add(buildJsonObject {
                                    put("title", item.title)
                                    put("type", item.lostType?.typeName)
                                    put("campus", item.campusName)
                                    put("time", item.createtime)
                                    put("linkman", item.linkman)
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
        description = "查询安徽大学官网最新的通知公告，返回标题、发布日期与原文链接。",
        parameters = schema(properties = buildJsonObject { }),
        timeoutMs = 30_000,
        kind = ToolKind.READ,
        source = "安徽大学官网",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        when (val result = LegacyCampusApi.universityNotices()) {
            is com.ahu.ahutong.core.common.AhuResult.Failure -> ToolOutput(
                ok = false,
                error = ToolError("notice_failed", result.error.toChinese()),
            )
            is com.ahu.ahutong.core.common.AhuResult.Success -> ToolOutput(
                ok = true,
                data = buildJsonObject {
                    put("notices", kotlinx.serialization.json.buildJsonArray {
                        result.value.take(20).forEach { notice ->
                            add(buildJsonObject {
                                put("title", notice.title)
                                put("publishedOn", notice.publishedOn)
                                put("url", notice.originalUrl)
                            })
                        }
                    })
                },
            )
        }
    }
}


class GetFreeRoomsTool : Tool {
    override val spec = ToolSpec(
        name = "get_free_rooms",
        displayName = "查询空闲教室",
        description = "查询指定日期（默认今天）某校区的空闲教室。campus: 1=磬苑校区，2=龙河校区；units 为节次，默认 1-13。",
        parameters = schema(
            properties = buildJsonObject {
                put("campus", intProp("校区：1=磬苑，2=龙河；默认 1", minimum = 1, maximum = 2))
                put("date", stringProp("日期 yyyy-MM-dd，默认今天"))
                put("units", stringProp("节次，逗号分隔，例如 1,2,3；默认全部 1-13"))
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
        when (val result = LegacyCampusApi.freeRooms(campus, date, units)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure -> ToolOutput(
                ok = false,
                error = ToolError("free_rooms_failed", result.error.toChinese()),
            )
            is com.ahu.ahutong.core.common.AhuResult.Success -> ToolOutput(
                ok = true,
                data = buildJsonObject {
                    put("date", date)
                    put("campus", campus)
                    put("count", result.value.size)
                    put("rooms", kotlinx.serialization.json.buildJsonArray {
                        result.value.take(60).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                },
            )
        }
    }
}
