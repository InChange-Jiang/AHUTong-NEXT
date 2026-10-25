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
import java.math.BigDecimal

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
        description = "查询成绩。term 不传时返回全部学期；返回课程、学分、分数与绩点。",
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

class RechargeCardTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "recharge_card",
        displayName = "校园卡充值",
        description = "从用户指定的银行卡真实扣款，给校园一卡通充值。只有用户在本轮明确给出金额和银行卡时才能调用；" +
            "金额单位为元（0.01-2000），bank 只能是 agricultural（中国农业银行）或 cmb（招商银行）。" +
            "失败后不要自动重试，先查余额确认是否已到账。",
        parameters = schema(
            properties = buildJsonObject {
                put("amount", numberProp("充值金额，单位元", minimum = 0.01, maximum = 2000.0))
                put("bank", stringProp("扣款银行卡：agricultural=中国农业银行；cmb=招商银行"))
            },
            required = listOf("amount", "bank"),
        ),
        timeoutMs = 180_000,
        kind = ToolKind.WRITE,
        source = "一卡通系统",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val amount = arguments.doubleOrNull("amount")
            ?: return invalid("缺少充值金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return invalid("充值金额需在 0.01 - 2000 元之间")
        }
        val bank = arguments.stringOrNull("bank")?.lowercase()
            ?: return invalid("缺少扣款银行卡")
        if (bank != "agricultural" && bank != "cmb") {
            return invalid("只支持 agricultural（中国农业银行）或 cmb（招商银行）")
        }
        val amountText = formatAmount(amount)
        val receipt = try {
            repository.rechargeCard(amountText, bank)
        } catch (failure: Exception) {
            return ToolOutput(
                ok = false,
                error = ToolError(
                    code = "recharge_failed",
                    message = failure.message ?: "充值失败，请先在余额里确认是否已到账",
                    retryable = false,
                ),
            )
        }
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("receipt", AgentJson.encodeToJsonElement(receipt))
            },
        )
    }

    private fun invalid(message: String): ToolOutput = ToolOutput(
        ok = false,
        error = ToolError("invalid_arguments", message),
    )
}

class RechargeElectricityTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "recharge_electricity",
        displayName = "宿舍电费充值",
        description = "从校园卡账户真实扣款，给设置里选定的宿舍房间充电费。只有用户本轮明确说出金额时才能调用；" +
            "金额单位为元（0.01-2000）。校园卡 6 位支付密码由用户在确认卡里输入，模型不要索要或传递密码。" +
            "失败后不要自动重试，先查电费确认是否已到账。",
        parameters = schema(
            properties = buildJsonObject {
                put("amount", numberProp("充值金额，单位元", minimum = 0.01, maximum = 2000.0))
            },
            required = listOf("amount"),
        ),
        timeoutMs = 180_000,
        kind = ToolKind.WRITE,
        source = "电费系统",
        requiresConfirmation = true,
        requiresPaymentPassword = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput =
        invalid("需要用户在确认卡里输入校园卡支付密码")

    override suspend fun describe(arguments: JsonObject): String? =
        repository.electricityRoomLabel()?.let { "房间：$it" }

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): ToolOutput {
        val amount = arguments.doubleOrNull("amount") ?: return invalid("缺少充值金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return invalid("充值金额需在 0.01 - 2000 元之间")
        }
        val password = secret?.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return invalid("请输入 6 位校园卡支付密码")
        val receipt = try {
            repository.rechargeElectricity(formatAmount(amount), password)
        } catch (failure: Exception) {
            return paymentFailure(failure.message ?: "电费充值失败，请先在电费里确认是否已到账")
        }
        return ToolOutput(
            ok = true,
            data = buildJsonObject { put("receipt", AgentJson.encodeToJsonElement(receipt)) },
        )
    }

    private fun invalid(message: String): ToolOutput = ToolOutput(
        ok = false,
        error = ToolError("invalid_arguments", message),
    )
}

class RechargeBathroomTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "recharge_bathroom",
        displayName = "浴室缴费",
        description = "从校园卡账户真实扣款，给浴室账户充值。只有用户本轮明确说出浴室、手机号和金额时才能调用；" +
            "bathroom 只能是「竹园/龙河」或「桔园/蕙园」，phone 是 11 位手机号，金额单位为元（0.01-2000）。" +
            "校园卡 6 位支付密码由用户在确认卡里输入，模型不要索要或传递密码。" +
            "失败后不要自动重试，先核对余额是否已到账。",
        parameters = schema(
            properties = buildJsonObject {
                put("bathroom", stringProp("浴室：「竹园/龙河」或「桔园/蕙园」"))
                put("phone", stringProp("浴室账户绑定的 11 位手机号"))
                put("amount", numberProp("缴费金额，单位元", minimum = 0.01, maximum = 2000.0))
            },
            required = listOf("bathroom", "phone", "amount"),
        ),
        timeoutMs = 180_000,
        kind = ToolKind.WRITE,
        source = "浴室缴费系统",
        requiresConfirmation = true,
        requiresPaymentPassword = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput =
        invalid("需要用户在确认卡里输入校园卡支付密码")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): ToolOutput {
        val bathroom = arguments.stringOrNull("bathroom")
            ?: return invalid("缺少浴室")
        if (bathroom != "竹园/龙河" && bathroom != "桔园/蕙园") {
            return invalid("浴室只能是「竹园/龙河」或「桔园/蕙园」")
        }
        val phone = arguments.stringOrNull("phone") ?: return invalid("缺少手机号")
        if (!phone.matches(Regex("\\d{11}"))) return invalid("请输入 11 位手机号")
        val amount = arguments.doubleOrNull("amount") ?: return invalid("缺少缴费金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return invalid("缴费金额需在 0.01 - 2000 元之间")
        }
        val password = secret?.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return invalid("请输入 6 位校园卡支付密码")
        val receipt = try {
            repository.rechargeBathroom(bathroom, phone, formatAmount(amount), password)
        } catch (failure: Exception) {
            return paymentFailure(failure.message ?: "浴室缴费失败，请先核对余额是否已到账")
        }
        return ToolOutput(
            ok = true,
            data = buildJsonObject { put("receipt", AgentJson.encodeToJsonElement(receipt)) },
        )
    }

    private fun invalid(message: String): ToolOutput = ToolOutput(
        ok = false,
        error = ToolError("invalid_arguments", message),
    )
}

class RechargeNetworkTool(private val repository: CampusRepository) : Tool {
    override val spec = ToolSpec(
        name = "recharge_network",
        displayName = "网费充值",
        description = "从校园卡账户真实扣款，给校园网账户充值。只有用户本轮明确说出金额时才能调用；" +
            "金额单位为元（0.01-2000）。校园卡 6 位支付密码由用户在确认卡里输入，模型不要索要或传递密码。" +
            "失败后不要自动重试，先确认是否已到账。",
        parameters = schema(
            properties = buildJsonObject {
                put("amount", numberProp("充值金额，单位元", minimum = 0.01, maximum = 2000.0))
            },
            required = listOf("amount"),
        ),
        timeoutMs = 180_000,
        kind = ToolKind.WRITE,
        source = "网费系统",
        requiresConfirmation = true,
        requiresPaymentPassword = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput =
        invalid("需要用户在确认卡里输入校园卡支付密码")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): ToolOutput {
        val amount = arguments.doubleOrNull("amount") ?: return invalid("缺少充值金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return invalid("充值金额需在 0.01 - 2000 元之间")
        }
        val password = secret?.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return invalid("请输入 6 位校园卡支付密码")
        val receipt = try {
            repository.rechargeNetwork(formatAmount(amount), password)
        } catch (failure: Exception) {
            return paymentFailure(failure.message ?: "网费充值失败，请先确认是否已到账")
        }
        return ToolOutput(
            ok = true,
            data = buildJsonObject { put("receipt", AgentJson.encodeToJsonElement(receipt)) },
        )
    }

    private fun invalid(message: String): ToolOutput = ToolOutput(
        ok = false,
        error = ToolError("invalid_arguments", message),
    )
}

private fun formatAmount(amount: Double): String =
    BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()

private fun paymentFailure(message: String): ToolOutput = ToolOutput(
    ok = false,
    error = ToolError("recharge_failed", message, retryable = false),
)

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
    RechargeCardTool(repository),
    RechargeElectricityTool(repository),
    RechargeBathroomTool(repository),
    RechargeNetworkTool(repository),
    AddCalendarEventTool(repository),
)

fun extensionTools(repository: CampusRepository): List<Tool> = listOf(
    GetWeatherTool(),
    GetAttendanceTool(),
    GetProgramCompletionTool(),
    GetLostFoundTool(),
    GetNoticesTool(),
    GetFreeRoomsTool(),
    GetEvaluationTasksTool(),
    SubmitEvaluationTool(),
    GetGpaRankTool(),
    GetCanteenRankingTool(),
    GetPhoneBookTool(),
    GetMailTool(),
    SendMailTool(),
    GetCourseTasksTool(),
    GetElectricityConditionsTool(),
    SwitchElectricityConditionTool(),
    PublishLostFoundTool(),
    DeleteLostFoundTool(),
    GetCalendarEventsTool(repository),
)

fun allCampusTools(repository: CampusRepository): List<Tool> =
    campusTools(repository) + extensionTools(repository)
