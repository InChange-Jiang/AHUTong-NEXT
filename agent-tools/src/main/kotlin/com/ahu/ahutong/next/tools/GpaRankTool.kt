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

class GetGpaRankTool : Tool {
    override val spec = ToolSpec(
        name = "get_gpa_rank",
        displayName = "查询绩点排名",
        description = "查询全程平均绩点、专业排名、总学分、最后更新时间，以及各学期绩点与专业排名。",
        parameters = schema(properties = buildJsonObject { }),
        timeoutMs = 60_000,
        kind = ToolKind.READ,
        source = "教务系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(false, error = ToolError("not_logged_in", "尚未登录安大通账号"))
        }
        when (val result = LegacyCampusApi.gpaRank()) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                ToolOutput(false, error = ToolError("gpa_rank_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> {
                val rank = result.value
                ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("gpa", rank.gpa)
                        put("majorRank", rank.majorRank)
                        put("majorHeadCount", rank.majorHeadCount)
                        put("totalCredits", rank.totalCredits)
                        put("updatedAt", rank.updatedDateTimeStr)
                        put("semesters", buildJsonArray {
                            rank.gpaSemesterSubs.forEach { semester ->
                                add(buildJsonObject {
                                    put("semesterId", semester.semesterId)
                                    put("gpa", semester.gpa)
                                    put("majorRank", semester.majorRank)
                                    put("totalCredits", semester.totalCredits)
                                })
                            }
                        })
                    },
                )
            }
        }
    }
}
