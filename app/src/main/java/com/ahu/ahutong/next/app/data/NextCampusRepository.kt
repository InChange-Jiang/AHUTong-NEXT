package com.ahu.ahutong.next.app.data

import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityUsagePredictor
import com.ahu.ahutong.next.data.Assignment
import com.ahu.ahutong.next.data.Balance
import com.ahu.ahutong.next.data.BillItem
import com.ahu.ahutong.next.data.BillSummary
import com.ahu.ahutong.next.data.CalendarEvent
import com.ahu.ahutong.next.data.CampusRepository
import com.ahu.ahutong.next.data.CourseOccurrence
import com.ahu.ahutong.next.data.Electricity
import com.ahu.ahutong.next.data.Exam
import com.ahu.ahutong.next.data.Grade
import com.ahu.ahutong.next.data.MockCampusRepository
import com.ahu.ahutong.next.data.RechargeReceipt
import com.ahu.ahutong.core.common.AhuResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 已登录时走旧工程真实数据层，未登录时退回演示数据。
 * 真实请求失败不会静默换成演示数据——宁可告诉用户“登录失效”，也不能把假数据当真的给。
 */
class NextCampusRepository(
    private val demo: MockCampusRepository = MockCampusRepository(),
) : CampusRepository {

    private val calendarStore = LocalCalendarStore(com.ahu.ahutong.core.common.AppEnvironmentHolder.context())
    private val assignmentStore = AssignmentCacheStore(com.ahu.ahutong.core.common.AppEnvironmentHolder.context())

    private val real: Boolean get() = LegacyCampusApi.isLoggedIn()

    override suspend fun schedule(days: Int): List<CourseOccurrence> {
        if (!real) return demo.schedule(days)
        val courses = LegacyCampusApi.scheduleOccurrences(days).orThrow()
        return courses.map {
            CourseOccurrence(
                date = it.date,
                weekday = it.weekday,
                name = it.name,
                teacher = it.teacher,
                location = it.location,
                startTime = it.startTime,
                endTime = it.endTime,
            )
        }
    }

    override suspend fun grades(term: String?): List<Grade> {
        if (!real) return demo.grades(term)
        val grade = LegacyCampusApi.grades().orThrow()
        val terms = grade.termGradeList.orEmpty()
            .filter { term == null || it.schoolYear.contains(term) || it.term == term }
        if (terms.isEmpty() && term == null && grade.termGradeList.isNullOrEmpty()) {
            throw IllegalStateException("教务系统暂时没有返回成绩")
        }
        return terms.flatMap { termBean ->
            val label = "${termBean.schoolYear} 第${termBean.term}学期"
            val semesterId = termBean.gradeList.orEmpty().firstOrNull()?.semesterId ?: 0
            termBean.gradeList.orEmpty().map { item ->
                val scoreText = item.grade?.takeIf { it.isNotBlank() }
                Grade(
                    course = item.course.orEmpty(),
                    credit = item.credit?.toDoubleOrNull() ?: 0.0,
                    score = scoreText?.toDoubleOrNull() ?: 0.0,
                    gpa = item.gradePoint?.toDoubleOrNull() ?: 0.0,
                    term = label,
                    scoreText = scoreText,
                    semesterId = semesterId,
                )
            }
        }
    }

    override suspend fun exams(): List<Exam> {
        if (!real) return demo.exams()
        return LegacyCampusApi.exams().orThrow().map {
            Exam(
                course = it.course.orEmpty(),
                time = it.time.orEmpty(),
                location = it.location.orEmpty(),
                seat = it.seatNum.orEmpty(),
            )
        }
    }

    // 未登录时继续用演示数据；已登录时查询真实一卡通与电费，失败直接报错，不冒充真实结果。
    override suspend fun balance(): Balance {
        if (!real) return demo.balance()
        val card = LegacyCampusApi.cardMoney().orThrow()
        val cardNo = when (val number = LegacyCampusApi.cardNumber()) {
            is AhuResult.Success -> number.value
            is AhuResult.Failure -> "校园卡"
        }
        return Balance(
            yuan = card.balance ?: 0.0,
            cardNo = cardNo,
            updatedAt = LocalDateTime.now().format(TIME_FORMAT),
        )
    }

    override suspend fun bills(): BillSummary {
        if (!real) return demo.bills()
        val now = LocalDateTime.now()
        val timeFrom = now.toLocalDate().withDayOfMonth(1).atStartOfDay().format(TIME_FORMAT)
        val timeTo = now.format(TIME_FORMAT)
        val summary = LegacyCampusApi.billSummary(timeFrom, timeTo).orThrow()
        val records = LegacyCampusApi.billPage(1, BILL_PAGE_SIZE, timeFrom, timeTo)
            .orThrow()
            .records
            .orEmpty()
            .sortedByDescending { it.effectdateStr.orEmpty() }
        val items = records.map { record ->
            val expense = record.isExpense
            val yuan = record.tranamt / 100.0
            BillItem(
                time = record.effectdateStr ?: record.jndatetimeStr.orEmpty(),
                place = record.displayPlace(),
                amount = if (expense) -yuan else yuan,
                kind = if (expense) "消费" else "充值",
            )
        }
        val topPlace = records
            .filter { it.isExpense }
            .groupingBy { it.displayPlace() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "暂无"
        return BillSummary(
            monthSpend = (summary.expenses ?: 0L) / 100.0,
            monthIncome = (summary.income ?: 0L) / 100.0,
            topPlace = topPlace,
            items = items,
        )
    }

    override suspend fun electricity(): Electricity {
        if (!real) return demo.electricity()
        val selection = LegacyCampusApi.savedElectricityRoom()?.takeIf { it.room != null }
            ?: throw IllegalStateException("还没有设置宿舍电费房间，请先到「设置 → 宿舍电费」里选一次")
        val controller = LegacyCampusApi.electricityController()
        val asOf = LocalDate.now()
        val snapshot = LegacyCampusApi.electricitySnapshot(controller, selection, asOf).orThrow()
        val estimatedDays = runCatching {
            ElectricityUsagePredictor.predict(snapshot, 1).daysRemaining?.roundToInt() ?: 0
        }.getOrDefault(0)
        val byDate = snapshot.dailyUsage
            .groupingBy { it.date }
            .fold(0.0) { total, reading -> total + reading.kwh }
        val lastSevenDays = (7 downTo 1).map { byDate[asOf.minusDays(it.toLong())] ?: 0.0 }
        return Electricity(
            room = roomLabel(selection),
            balanceKwh = snapshot.remainingKwh,
            dailyUsage = lastSevenDays,
            estimatedDays = estimatedDays,
        )
    }

    override suspend fun assignments(days: Int): List<Assignment> {
        if (!LegacyCampusApi.hasChaoxingSession()) return demo.assignments(days)
        return filterAssignments(assignmentStore.load(assignmentAccountId()), days)
    }

    override suspend fun refreshAssignments(days: Int): List<Assignment> {
        if (!LegacyCampusApi.hasChaoxingSession()) return demo.assignments(days)
        val fresh = fetchAssignments()
        assignmentStore.save(assignmentAccountId(), fresh)
        return filterAssignments(fresh, days)
    }

    private fun assignmentAccountId(): String =
        LegacyCampusApi.currentStudentId().orEmpty().ifBlank { "anon" }

    private suspend fun fetchAssignments(): List<Assignment> =
        LegacyCampusApi.assignments().orThrow().map { work ->
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val zone = java.time.ZoneId.systemDefault()
            fun format(epoch: Long?): String? = epoch?.let {
                java.time.Instant.ofEpochMilli(it).atZone(zone).format(formatter)
            }
            val endAt = format(work.endTs)
            Assignment(
                course = work.courseName,
                title = work.title,
                dueAt = endAt ?: work.rawEnd,
                status = if (work.isDone) "已完成" else "未提交",
                startAt = format(work.startTs),
                endAt = endAt,
            )
        }

    private fun filterAssignments(items: List<Assignment>, days: Int): List<Assignment> {
        val today = LocalDate.now()
        val upper = today.plusDays(days.toLong())
        val lower = today.minusDays(14)
        return items.filter { work ->
            val date = runCatching { LocalDate.parse(work.dueAt.take(10)) }.getOrNull()
            date == null || (date >= lower && date <= upper)
        }.sortedWith(compareBy({ it.status.contains("已完成") }, { it.dueAt }))
    }

    override fun electricityRoomLabel(): String? =
        LegacyCampusApi.savedElectricityRoom()
            ?.takeIf { it.room != null }
            ?.let(::roomLabel)

    override suspend fun rechargeCard(amountYuan: String, bank: String): RechargeReceipt {
        if (!real) throw IllegalStateException("请先登录安大通账号再充值")
        val bankType = when (bank.lowercase()) {
            "agricultural" -> CardRechargeBank.AGRICULTURAL_BANK
            "cmb" -> CardRechargeBank.CHINA_MERCHANTS_BANK
            else -> throw IllegalArgumentException("不支持的银行卡：$bank")
        }
        val amount = amountOf(amountYuan)
        val outcome = LegacyCampusApi.rechargeCard(amountYuan, bankType).orThrow()
        return RechargeReceipt(
            orderId = outcome.orderId,
            amountYuan = amount,
            channel = when (bankType) {
                CardRechargeBank.AGRICULTURAL_BANK -> "中国农业银行"
                CardRechargeBank.CHINA_MERCHANTS_BANK -> "招商银行"
                CardRechargeBank.ALIPAY -> "支付宝"
            },
            target = "校园卡",
            message = outcome.message,
        )
    }

    override suspend fun rechargeElectricity(amountYuan: String, password: String): RechargeReceipt {
        if (!real) throw IllegalStateException("请先登录安大通账号再充值")
        if (password.length != 6 || password.any { !it.isDigit() }) {
            throw IllegalArgumentException("请输入 6 位校园卡支付密码")
        }
        val amount = amountOf(amountYuan)
        val selection = LegacyCampusApi.savedElectricityRoom()?.takeIf { it.room != null }
            ?: throw IllegalStateException("还没有设置宿舍电费房间，请先到「设置 → 宿舍电费」里选一次")
        val receipt = LegacyCampusApi.rechargeElectricity(amountYuan, password).orThrow()
        return RechargeReceipt(
            orderId = receipt.reference,
            amountYuan = amount,
            channel = "校园卡账户",
            target = roomLabel(selection),
            message = "电费充值成功",
        )
    }

    override suspend fun rechargeBathroom(
        bathroom: String,
        phone: String,
        amountYuan: String,
        password: String,
    ): RechargeReceipt {
        if (!real) throw IllegalStateException("请先登录安大通账号再缴费")
        if (bathroom !in BATHROOMS) throw IllegalArgumentException("不支持的浴室：$bathroom")
        if (!phone.matches(Regex("\\d{11}"))) throw IllegalArgumentException("请输入 11 位手机号")
        if (password.length != 6 || password.any { !it.isDigit() }) {
            throw IllegalArgumentException("请输入 6 位校园卡支付密码")
        }
        val amount = amountOf(amountYuan)
        val account = LegacyCampusApi.bathroomAccount(bathroom, phone).orThrow()
            .map?.data ?: throw IllegalStateException("未查询到浴室账户，请核对手机号")
        val receipt = LegacyCampusApi.rechargeBathroom(
            bathroom = bathroom,
            amountYuan = amountYuan,
            password = password,
            account = account,
        ).orThrow()
        return RechargeReceipt(
            orderId = receipt.reference,
            amountYuan = amount,
            channel = "校园卡账户",
            target = "$bathroom · $phone",
            message = "浴室缴费成功",
        )
    }

    override suspend fun rechargeNetwork(amountYuan: String, password: String): RechargeReceipt {
        if (!real) throw IllegalStateException("请先登录安大通账号再充值")
        if (password.length != 6 || password.any { !it.isDigit() }) {
            throw IllegalArgumentException("请输入 6 位校园卡支付密码")
        }
        val amount = amountOf(amountYuan)
        val receipt = LegacyCampusApi.rechargeNetwork(amountYuan, password).orThrow()
        return RechargeReceipt(
            orderId = receipt.reference,
            amountYuan = amount,
            channel = "校园卡账户",
            target = "网费账户",
            message = "网费充值成功",
        )
    }

    private fun amountOf(text: String): Double =
        text.toDoubleOrNull()?.takeIf { it > 0.0 } ?: throw IllegalArgumentException("充值金额无效")

    override suspend fun addCalendarEvent(
        title: String,
        start: String,
        end: String,
        reminderMinutes: Int,
    ): CalendarEvent {
        val event = demo.addCalendarEvent(title, start, end, reminderMinutes)
        calendarStore.add(event)
        return event
    }

    override fun calendarEvents(): List<CalendarEvent> = calendarStore.load()

    fun deleteCalendarEvent(id: String): List<CalendarEvent> = calendarStore.delete(id)

    private fun <T> AhuResult<T>.orThrow(): T = when (this) {
        is AhuResult.Success -> value
        is AhuResult.Failure -> throw IllegalStateException(error.toChinese())
    }

    private companion object {
        const val BILL_PAGE_SIZE = 50
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val BATHROOMS = setOf("竹园/龙河", "桔园/蕙园")
    }
}

private fun roomLabel(selection: RoomSelectionInfo): String {
    val campus = selection.campus?.name
    val building = selection.building?.name
    val floor = selection.floor?.name
    val room = selection.room?.name
    // 上游的 room.name 常已包含楼栋甚至楼层（如「兰园C楼空调-4-415」），避免重复拼接。
    return if (room != null && building != null && room.contains(building)) {
        listOfNotNull(campus, room).joinToString(" ")
    } else {
        listOfNotNull(campus, building, floor, room).joinToString(" ")
    }
}

private fun TurnoverRecord.displayPlace(): String =
    toMerchant?.takeIf { it.isNotBlank() }
        ?: resume?.takeIf { it.isNotBlank() }
        ?: consumeTypeName?.takeIf { it.isNotBlank() }
        ?: turnoverType?.takeIf { it.isNotBlank() }
        ?: "校园卡"
