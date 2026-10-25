package com.ahu.ahutong.next.tools

import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import com.ahu.ahutong.next.data.CampusRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetCalendarEventsTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_calendar_events",
        displayName = "读取本地日程",
        description = "读取本地日历中的日程。可按年、月或日期区间筛选；返回标题、开始/结束时间和提醒设置。",
        parameters = schema(
            properties = buildJsonObject {
                put("year", intProp("年份，例如 2026"))
                put("month", intProp("月份 1-12；需同时传 year", minimum = 1, maximum = 12))
                put("from", stringProp("开始日期 yyyy-MM-dd，包含当天"))
                put("to", stringProp("结束日期 yyyy-MM-dd，包含当天"))
                put("limit", intProp("最多返回条数，默认 50", minimum = 1, maximum = 200))
            },
        ),
        timeoutMs = 5_000,
        kind = ToolKind.READ,
        source = "本地日历",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val year = arguments.intOr("year", 0).takeIf { it > 0 }
        val month = arguments.intOr("month", 0).takeIf { it in 1..12 }
        val from = arguments.stringOrNull("from")
        val to = arguments.stringOrNull("to")
        val limit = arguments.intOr("limit", 50).coerceIn(1, 200)
        val monthPrefix = if (year != null && month != null) {
            "%04d-%02d".format(year, month)
        } else null
        val events = repository.calendarEvents()
            .filter { event ->
                val date = event.start.take(10)
                (monthPrefix == null || date.startsWith(monthPrefix)) &&
                    (from == null || date >= from) &&
                    (to == null || date <= to)
            }
            .sortedBy { it.start }
            .take(limit)
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("count", events.size)
                put("events", buildJsonArray {
                    events.forEach { event ->
                        add(buildJsonObject {
                            put("id", event.id)
                            put("title", event.title)
                            put("start", event.start)
                            put("end", event.end)
                            put("reminderMinutes", event.reminderMinutes)
                        })
                    }
                })
            },
        )
    }
}
