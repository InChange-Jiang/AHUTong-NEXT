package com.ahu.ahutong.next.tools

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

class GetCourseTasksTool : Tool {
    override val spec = ToolSpec(
        name = "get_course_tasks",
        displayName = "查询课程任务进度",
        description = "查询学习通课程的章节任务点完成进度。默认读本地缓存；refresh=true 时重新同步学习通。",
        parameters = schema(
            properties = buildJsonObject {
                put("refresh", boolProp("是否重新同步学习通，默认 false"))
            },
        ),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "学习通",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(false, error = ToolError("not_logged_in", "尚未登录安大通账号"))
        }
        val refresh = arguments.boolOr("refresh", false)
        val cached = LegacyCampusApi.cachedCourseProgress()
        val progress = if (refresh || cached.isEmpty()) {
            when (val result = LegacyCampusApi.syncCourseProgress()) {
                is com.ahu.ahutong.core.common.AhuResult.Failure ->
                    return@withContext ToolOutput(false, error = ToolError("course_tasks_failed", result.error.toChinese()))
                is com.ahu.ahutong.core.common.AhuResult.Success -> result.value
            }
        } else {
            cached
        }
        ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("refreshed", refresh || cached.isEmpty())
                put("courseCount", progress.size)
                put("doneCount", progress.sumOf { it.doneCount })
                put("totalCount", progress.sumOf { it.totalCount })
                put("courses", buildJsonArray {
                    progress.forEach { course ->
                        add(buildJsonObject {
                            put("name", course.name)
                            put("done", course.doneCount)
                            put("total", course.totalCount)
                            put("percent", course.percent)
                            put("updatedAt", course.updatedAt)
                        })
                    }
                })
            },
        )
    }
}
