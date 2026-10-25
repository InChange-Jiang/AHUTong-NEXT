package com.ahu.ahutong.next.tools

import com.ahu.ahutong.data.phonebook.PhoneBookDirectory
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetPhoneBookTool : Tool {
    override val spec = ToolSpec(
        name = "get_phone_book",
        displayName = "查询电话本",
        description = "查询安徽大学校内公开电话。可按部门、名称或号码搜索；结果包含所属分组与号码。",
        parameters = schema(
            properties = buildJsonObject {
                put("query", stringProp("关键词，例如 教务处、报修、6386"))
                put("department", stringProp("限定分组，例如 磬苑后勤、报警电话"))
                put("limit", intProp("最多返回条数，默认 30", minimum = 1, maximum = 100))
            },
        ),
        timeoutMs = 5_000,
        kind = ToolKind.READ,
        source = "校内电话本",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val query = arguments.stringOrNull("query")?.trim()
        val department = arguments.stringOrNull("department")?.trim()
        if (query == null && department == null) {
            return ToolOutput(false, error = ToolError("invalid_arguments", "请提供 query 或 department"))
        }
        val limit = arguments.intOr("limit", 30).coerceIn(1, 100)
        val matches = PhoneBookDirectory.groups
            .filter { (group, _) -> department == null || group.contains(department) }
            .flatMap { (group, entries) ->
                entries.map { entry -> Triple(group, entry, entry.name) }
            }
            .filter { (group, entry, _) ->
                query == null ||
                    entry.name.contains(query, ignoreCase = true) ||
                    group.contains(query, ignoreCase = true) ||
                    entry.numbers.any { it.contains(query) }
            }
            .take(limit)
        if (matches.isEmpty()) {
            return ToolOutput(false, error = ToolError("phone_book_empty", "没有找到匹配的电话"))
        }
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("count", matches.size)
                put("entries", buildJsonArray {
                    matches.forEach { (group, entry, _) ->
                        add(buildJsonObject {
                            put("group", group)
                            put("name", entry.name)
                            put("numbers", buildJsonArray { entry.numbers.forEach { add(JsonPrimitive(it)) } })
                        })
                    }
                })
            },
        )
    }
}
