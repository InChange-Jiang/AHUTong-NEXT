package com.ahu.ahutong.next.tools

import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import com.ahu.ahutong.next.data.CampusRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

class GetScheduleTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_schedule",
        displayName = "查询课表",
        description = "查询从今天起指定天数内的课程安排。days 默认 7，范围 1-30。",
        parameters = schema(
            properties = buildJsonObject {
                put("days", intProp("查询天数，默认 7", minimum = 1, maximum = 30))
            },
        ),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "教务系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val days = arguments.intOr("days", 7).coerceIn(1, 30)
        val courses = repository.schedule(days)
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("days", days)
                put("count", courses.size)
                put("courses", AgentJson.encodeToJsonElement(courses))
            },
        )
    }
}

class GetAssignmentsTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_assignments",
        displayName = "查询作业",
        description = "查询学习通里未完成和近期的作业，返回课程、标题、截止时间与状态。days 默认 14，范围 1-30。",
        parameters = schema(
            properties = buildJsonObject {
                put("days", intProp("查询天数，默认 14", minimum = 1, maximum = 30))
            },
        ),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "学习通",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val days = arguments.intOr("days", 14).coerceIn(1, 30)
        val assignments = repository.assignments(days)
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("days", days)
                put("count", assignments.size)
                put("assignments", AgentJson.encodeToJsonElement(assignments))
            },
        )
    }
}

class GetGradesTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_grades",
        displayName = "查询成绩",
        description = "查询指定学期的成绩。term 不传时返回当前学期；返回课程、学分、分数与绩点。",
        parameters = schema(
            properties = buildJsonObject {
                put("term", stringProp("学期名称，例如 2026-2027 学年第一学期；不传则用当前学期"))
            },
        ),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "教务系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val term = arguments.stringOrNull("term")
        val grades = repository.grades(term)
        val credits = grades.sumOf { it.credit }
        val gpaAverage = if (grades.isEmpty()) 0.0 else grades.sumOf { it.gpa * it.credit } / credits
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("count", grades.size)
                put("credits", credits)
                put("gpaAverage", (gpaAverage * 100).toInt() / 100.0)
                put("grades", AgentJson.encodeToJsonElement(grades))
            },
        )
    }
}

class GetBalanceTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_balance",
        displayName = "查询一卡通余额",
        description = "查询校园一卡通的当前余额与卡号。",
        parameters = schema(buildJsonObject { }),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "一卡通系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val balance = repository.balance()
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("balance", AgentJson.encodeToJsonElement(balance))
            },
        )
    }
}

class GetBillsTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_bills",
        displayName = "查询一卡通账单",
        description = "查询校园一卡通近期流水：本月消费、充值、消费地点和逐笔明细。",
        parameters = schema(buildJsonObject { }),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "一卡通系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val bills = repository.bills()
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("bills", AgentJson.encodeToJsonElement(bills))
            },
        )
    }
}

class GetExamsTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_exams",
        displayName = "查询考试安排",
        description = "查询教务系统里的期末考试安排：课程、时间、地点与座位号。",
        parameters = schema(buildJsonObject { }),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "教务系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val exams = repository.exams()
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("count", exams.size)
                put("exams", AgentJson.encodeToJsonElement(exams))
            },
        )
    }
}

class GetElectricityTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "get_electricity",
        displayName = "查询宿舍电费",
        description = "查询宿舍剩余电量、近七日用电量和预计可用天数。",
        parameters = schema(buildJsonObject { }),
        timeoutMs = 120_000,
        kind = ToolKind.READ,
        source = "电费系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val electricity = repository.electricity()
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("electricity", AgentJson.encodeToJsonElement(electricity))
            },
        )
    }
}

class AddCalendarEventTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "add_calendar_event",
        displayName = "添加日程",
        description = "在本地日历中新建一条日程。此操作会写入数据，必须先由用户确认。",
        parameters = schema(
            properties = buildJsonObject {
                put("title", stringProp("日程标题"))
                put("start", stringProp("开始时间，格式 yyyy-MM-dd HH:mm"))
                put("end", stringProp("结束时间，格式 yyyy-MM-dd HH:mm"))
                put("reminder_minutes", intProp("提前多少分钟提醒，默认 15", minimum = 0, maximum = 1440))
            },
            required = listOf("title", "start", "end"),
        ),
        kind = ToolKind.WRITE,
        source = "本地日历",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val title = arguments.stringOrNull("title")
            ?: return invalid("title 不能为空")
        val start = arguments.stringOrNull("start")
            ?: return invalid("start 不能为空")
        val end = arguments.stringOrNull("end")
            ?: return invalid("end 不能为空")
        val reminder = arguments.intOr("reminder_minutes", 15).coerceIn(0, 1440)

        val event = repository.addCalendarEvent(
            title = title,
            start = start,
            end = end,
            reminderMinutes = reminder,
        )
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("event", AgentJson.encodeToJsonElement(event))
            },
        )
    }

    private fun invalid(message: String): ToolOutput = ToolOutput(
        ok = false,
        error = ToolError("invalid_arguments", message),
    )
}

fun campusTools(repository: CampusRepository): List<Tool> = listOf(
    GetScheduleTool(repository),
    GetAssignmentsTool(repository),
    GetGradesTool(repository),
    GetBalanceTool(repository),
    GetBillsTool(repository),
    GetExamsTool(repository),
    GetElectricityTool(repository),
    AddCalendarEventTool(repository),
)
