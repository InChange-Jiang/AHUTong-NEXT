package com.ahu.ahutong.legacy

import android.content.Context
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.AppEnvironment
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.common.map
import com.ahu.ahutong.data.EvaluationRepository
import com.ahu.ahutong.data.EvalQuestionnaireForm
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.FreeClassroomGateway
import com.ahu.ahutong.data.adapter.CardRechargeOutcome
import com.ahu.ahutong.data.adapter.RepositoryBathroomDepositSource
import com.ahu.ahutong.data.adapter.RepositoryCardRechargeSource
import com.ahu.ahutong.data.adapter.RepositoryElectricityDepositSource
import com.ahu.ahutong.data.adapter.RepositoryElectricityUsageSource
import com.ahu.ahutong.data.adapter.RepositoryNetworkRechargeSource
import com.ahu.ahutong.data.crawler.model.jwxt.DateTimeSegmentCmd
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsRequest
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverPage
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.EvalCheckParam
import com.ahu.ahutong.data.model.EvalQuestion
import com.ahu.ahutong.data.model.EvalSemester
import com.ahu.ahutong.data.model.EvalSubmitRequest
import com.ahu.ahutong.data.model.EvalTask
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.crawler.model.jwxt.StudentInfo
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.data.server.model.SchoolCalendarYearsResponse
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.notice.CampusNotice
import com.ahu.ahutong.data.notice.UniversityNoticeCrawler
import com.ahu.ahutong.data.notice.UniversityNoticeHttpFetcher
import com.ahu.ahutong.data.recharge.BathroomPayment
import com.ahu.ahutong.data.recharge.ElectricityOptionLevel
import com.ahu.ahutong.data.recharge.ElectricityRoom
import com.ahu.ahutong.data.recharge.ElectricityOptionQuery
import com.ahu.ahutong.data.recharge.ElectricityPayment
import com.ahu.ahutong.data.recharge.ElectricityUsageSnapshot
import com.ahu.ahutong.data.recharge.NetworkRechargeSnapshot
import com.ahu.ahutong.data.recharge.RechargeReceipt
import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.data.schedule.TeachingWeekPolicy
import com.ahu.ahutong.data.session.DefaultAhuSession
import com.ahu.ahutong.data.session.SavedAccounts
import com.ahu.ahutong.data.session.SecureCredentialVault
import com.ahu.ahutong.data.session.SessionStore
import com.tencent.mmkv.MMKV
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 旧数据层对 NEXT 的唯一入口。旧工程的包名、模型与协议实现保持原样，
 * 新应用只通过这里拿到会话与只读数据，避免把旧 UI/DI 依赖带进来。
 */
object LegacyCampusApi {

    const val WEB_LOGIN_URL = "https://jw.ahu.edu.cn/student/sso/login"

    @Volatile
    private var initialized = false

    /** WebView 验证通过后、cookie 导入前，暂时保留已认证的智慧安大身份。 */
    @Volatile
    private var pendingWebUser: User? = null

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            AppEnvironmentHolder.install(object : AppEnvironment {
                override val context: Context = app
            })
            MMKV.initialize(app)
            AHURepository.initializeDataSource(useMock = false)
            initialized = true
        }
    }

    fun isLoggedIn(): Boolean = initialized && AHUCache.isLogin()

    fun currentUserName(): String? = SessionStore.currentUser()?.name

    fun currentStudentId(): String? = SessionStore.currentUser()?.xh

    sealed interface LoginResult {
        data class Success(val name: String) : LoginResult
        /** 智慧安大已通过，教务需要用户在 WebView 中完成一次安全验证。 */
        data class WebVerificationRequired(val name: String) : LoginResult
        data class Failure(val message: String) : LoginResult
    }

    suspend fun login(username: String, password: String): LoginResult {
        val result = AHURepository.loginWithCrawler(username.trim(), password)
        return when (result) {
            is AhuResult.Failure -> LoginResult.Failure(result.error.toChinese())
            is AhuResult.Success -> when (val outcome = result.value) {
                is LoginOutcome.Success -> {
                    persistUser(outcome.user, password)
                    LoginResult.Success(outcome.user.name.orEmpty())
                }
                is LoginOutcome.JwxtWebVerificationRequired -> {
                    pendingWebUser = outcome.user
                    LoginResult.WebVerificationRequired(outcome.user.name.orEmpty())
                }
            }
        }
    }

    suspend fun completeWebVerification(cookiesJson: String): LoginResult {
        val user = pendingWebUser ?: return LoginResult.Failure("登录信息已失效，请重新登录")
        val result = AHURepository.importWebLoginCookies(cookiesJson)
        return when (result) {
            is AhuResult.Success -> {
                pendingWebUser = null
                persistUser(user, null)
                runCatching { DefaultAhuSession.completeWebVerification() }
                LoginResult.Success(user.name.orEmpty())
            }
            is AhuResult.Failure -> LoginResult.Failure(result.error.toChinese())
        }
    }

    fun logout() {
        pendingWebUser = null
        runCatching { AHUCache.clearCurrentUser() }
        runCatching { SecureCredentialVault.clearWisdomPassword() }
        runCatching {
            com.ahu.ahutong.data.crawler.manager.CookieManager.cookieJar.clear()
        }
    }

    private fun persistUser(user: User, password: String?) {
        password?.let { SecureCredentialVault.saveWisdomPassword(it) }
        AHUCache.saveCurrentUser(user)
        if (!password.isNullOrBlank()) {
            runCatching { SavedAccounts.save(user, password) }
        }
    }

    fun hasChaoxingSession(): Boolean = initialized && Store.hasCookie()

    fun cachedCourseProgress(): List<CourseProgress> = Store.getCourseProgress()

    suspend fun syncCourseProgress(): AhuResult<List<CourseProgress>> = withContext(Dispatchers.IO) {
        if (!hasChaoxingSession()) {
            return@withContext AhuResult.Failure(AhuError.Unauthorized("学习通未连接，请先登录"))
        }
        runCatching { ChaoxingApi(AppEnvironmentHolder.context()).syncCourseProgress() }
            .fold(
                onSuccess = { AhuResult.Success(it) },
                onFailure = { AhuResult.Failure(AhuError.Unknown(it.message ?: "课程任务同步失败")) },
            )
    }

    /** 把当前已登录账号补进“保存的账号”，用于旧版本升级后的首次打开。 */
    fun rememberCurrentAccount() {
        val user = SessionStore.currentUser() ?: return
        if (user.xh.isNullOrBlank()) return
        val password = SecureCredentialVault.wisdomPassword()?.takeIf { it.isNotBlank() } ?: return
        runCatching { SavedAccounts.save(user, password) }
    }

    suspend fun loginChaoxing(account: String, password: String): AhuResult<Unit> =
        LegacyDataApi.loginChaoxing(account, password)

    suspend fun assignments(): AhuResult<List<Work>> = LegacyDataApi.assignments()

    /** 空闲教室：先取校区楼栋，再逐楼查询。 */
    suspend fun freeRooms(campusId: Int, date: String, units: List<String>): AhuResult<List<String>> =
        runCatching {
            val buildings = FreeClassroomGateway.buildings(campusId)
            buildings.flatMap { building ->
                FreeClassroomGateway.freeRooms(
                    GetFreeRoomsRequest(
                        buildingId = building.id.toString(),
                        campusId = campusId.toString(),
                        dateTimeSegmentCmd = DateTimeSegmentCmd(
                            startDateTime = date,
                            endDateTime = date,
                            units = units,
                        ),
                    ),
                ).roomList.map { room ->
                    "${building.nameZh} ${room.nameZh} · ${room.floor} 楼 · ${room.seats} 座"
                }
            }.distinct()
        }.fold(
            onSuccess = { AhuResult.Success(it) },
            onFailure = { AhuResult.Failure(AhuError.Unknown(it.message ?: "空闲教室查询失败")) },
        )

    /** 校官网通知公告（公开页面，不携带登录 Cookie）。 */
    suspend fun universityNotices(): AhuResult<List<CampusNotice>> = runCatching {
        UniversityNoticeCrawler(UniversityNoticeHttpFetcher()).fetchPage(null).notices
    }.fold(
        onSuccess = { AhuResult.Success(it) },
        onFailure = { AhuResult.Failure(AhuError.Unknown(it.message ?: "通知查询失败")) },
    )

    suspend fun schedule(days: Int = 7): AhuResult<List<Course>> {
        return AHURepository.getSchedule(isRefresh = false)
    }

    suspend fun grades(term: String? = null) = AHURepository.getGrade(isRefresh = false)

    suspend fun exams(): AhuResult<List<com.ahu.ahutong.data.model.Exam>> {
        val user = SessionStore.currentUser()
            ?: return AhuResult.Failure(AhuError.Unauthorized("尚未登录"))
        return AHURepository.getExamInfo(
            isRefresh = false,
            studentID = user.xh.orEmpty(),
            studentName = user.name.orEmpty(),
        )
    }

    // ---- 教务扩展 ----

    suspend fun studentInfo(force: Boolean = false): AhuResult<StudentInfo> =
        AHURepository.getStudentInfo(force)

    suspend fun gpaRank(): AhuResult<GpaRankInfo> {
        val user = SessionStore.currentUser()
            ?: return AhuResult.Failure(AhuError.Unauthorized("尚未登录"))
        val studentId = user.xh.orEmpty()
        return when (val result = AHURepository.getGpaRankInfo(studentId)) {
            is AhuResult.Success -> {
                AHUCache.saveGpaRankInfo(studentId, result.value)
                result
            }
            is AhuResult.Failure ->
                AHUCache.getGpaRankInfo(studentId)?.let { AhuResult.Success(it) } ?: result
        }
    }

    suspend fun scheduleSemesters(): AhuResult<List<ScheduleSemester>> =
        AHURepository.getScheduleSemesters()

    suspend fun semesterSchedule(semesterId: Int): AhuResult<List<Course>> =
        AHURepository.getSemesterSchedule(semesterId)

    suspend fun paymentQr(): AhuResult<String> = AHURepository.getQrcode()

    suspend fun schoolCalendarYears(): AhuResult<SchoolCalendarYearsResponse> =
        AHURepository.getSchoolCalendarYears()

    suspend fun schoolCalendar(year: String): AhuResult<ByteArray> =
        when (val result = AHURepository.getSchoolCalendar(year)) {
            is AhuResult.Failure -> result
            is AhuResult.Success -> runCatching {
                result.value.body()?.bytes() ?: ByteArray(0)
            }.fold(
                onSuccess = { AhuResult.Success(it) },
                onFailure = { AhuResult.Failure(AhuError.Unknown(it.message ?: "校历下载失败")) },
            )
        }

    suspend fun evaluationSemesters(): AhuResult<List<EvalSemester>> =
        EvaluationRepository.getSemesters()

    suspend fun evaluationTasks(
        semesterId: String,
        evaluated: Boolean = false,
    ): AhuResult<List<EvalTask>> =
        EvaluationRepository.getEvaluationList(semesterId, evaluated).map { items ->
            items.flatMap { it.taskList }
        }

    suspend fun evaluationForm(questionnaireId: String): AhuResult<EvalQuestionnaireForm> =
        EvaluationRepository.getQuestions(questionnaireId)

    suspend fun evaluationCheck(stdTaskId: String): AhuResult<EvalCheckParam> =
        EvaluationRepository.checkParam(stdTaskId)

    suspend fun submitEvaluation(request: EvalSubmitRequest): AhuResult<Unit> =
        EvaluationRepository.submit(request)

    // ---- 校园卡余额与账单（只读，不涉及支付） ----

    private val electricityDeposit = RepositoryElectricityDepositSource()
    private val electricityUsage = RepositoryElectricityUsageSource()
    private val cardRecharge = RepositoryCardRechargeSource()
    private val bathroomDeposit = RepositoryBathroomDepositSource()
    private val networkRecharge = RepositoryNetworkRechargeSource()

    /** 一卡通余额，优先走实时 queryCard。 */
    suspend fun cardMoney(): AhuResult<com.ahu.ahutong.data.model.Card> =
        withContext(Dispatchers.IO) {
            AHURepository.useCrawlerDataSource()
            AHURepository.getCardMoney(preferRealtime = true)
        }

    suspend fun cardNumber(): AhuResult<String> = withContext(Dispatchers.IO) {
        AHURepository.useCrawlerDataSource()
        when (val result = AHURepository.getCardInfo()) {
            is AhuResult.Failure -> result
            is AhuResult.Success -> {
                val card = result.value.data.card.firstOrNull()
                val number = card?.sno?.takeIf { it.isNotBlank() } ?: card?.account.orEmpty()
                if (number.isBlank()) {
                    AhuResult.Failure(AhuError.ProtocolChanged("校园卡信息里没有卡号"))
                } else {
                    AhuResult.Success(number)
                }
            }
        }
    }

    suspend fun billSummary(timeFrom: String, timeTo: String): AhuResult<TurnoverCount> =
        withContext(Dispatchers.IO) {
            AHURepository.useCrawlerDataSource()
            AHURepository.getBillSummary(timeFrom, timeTo)
        }

    suspend fun billPage(
        page: Int,
        size: Int,
        timeFrom: String,
        timeTo: String,
    ): AhuResult<TurnoverPage> = withContext(Dispatchers.IO) {
        AHURepository.useCrawlerDataSource()
        AHURepository.getBillPage(
            page = page,
            size = size,
            timeFrom = timeFrom,
            timeTo = timeTo,
            type = null,
        )
    }

    /**
     * 校园卡充值：查卡 → 开单 → 扣款。真实资金操作，调用方必须先取得用户确认。
     * 金额单位为元，成功时返回订单号与上游消息。
     */
    suspend fun rechargeCard(
        amountYuan: String,
        bank: CardRechargeBank,
    ): AhuResult<CardRechargeOutcome> {
        AHURepository.useCrawlerDataSource()
        return cardRecharge.recharge(amountYuan, bank)
    }

    // ---- 宿舍电费：房间选择 + 用电快照（只读，不涉及缴费） ----

    fun savedElectricityRoom(): RoomSelectionInfo? = AHUCache.getRoomSelection()

    fun electricityController(): ElectricityController = AHUCache.getElectricityController()

    fun saveElectricitySelection(controller: ElectricityController, selection: RoomSelectionInfo) {
        AHUCache.setElectricityController(controller)
        AHUCache.saveRoomSelection(selection)
    }

    suspend fun electricityInitialOptions(
        controller: ElectricityController,
    ): AhuResult<List<CampusDataItem>> = electricityDeposit.options(
        ElectricityOptionQuery(controller, emptyElectricitySelection(controller), ElectricityOptionLevel.Initial),
    )

    suspend fun electricityBuildings(
        controller: ElectricityController,
        campus: CampusDataItem,
    ): AhuResult<List<CampusDataItem>> = electricityDeposit.options(
        ElectricityOptionQuery(
            controller,
            RoomSelectionInfo(campus = campus, building = null, floor = null, room = null, controller = controller),
            ElectricityOptionLevel.Buildings,
        ),
    )

    suspend fun electricityFloors(
        controller: ElectricityController,
        selection: RoomSelectionInfo,
    ): AhuResult<List<CampusDataItem>> = electricityDeposit.options(
        ElectricityOptionQuery(controller, selection, ElectricityOptionLevel.Floors),
    )

    suspend fun electricityRooms(
        controller: ElectricityController,
        selection: RoomSelectionInfo,
    ): AhuResult<List<CampusDataItem>> = electricityDeposit.options(
        ElectricityOptionQuery(controller, selection, ElectricityOptionLevel.Rooms),
    )

    suspend fun electricitySnapshot(
        controller: ElectricityController,
        selection: RoomSelectionInfo,
        asOf: LocalDate,
    ): AhuResult<ElectricityUsageSnapshot> = electricityUsage.snapshot(controller, selection, asOf)

    suspend fun electricityRoomInfo(): AhuResult<ElectricityRoom> {
        val selection = savedElectricityRoom()?.takeIf { it.room != null }
            ?: return AhuResult.Failure(AhuError.Unknown("还没有设置宿舍电费房间，请先在设置里选一次"))
        return electricityDeposit.room(electricityController(), selection)
    }

    /** 电费缴费需要当前房间的支付参数；房间来自设置里保存的选择。 */
    suspend fun rechargeElectricity(
        amountYuan: String,
        password: String,
    ): AhuResult<RechargeReceipt> {
        val selection = savedElectricityRoom()?.takeIf { it.room != null }
            ?: return AhuResult.Failure(AhuError.Unknown("还没有设置宿舍电费房间，请先在设置里选一次"))
        val controller = electricityController()
        val room = when (val result = electricityDeposit.room(controller, selection)) {
            is AhuResult.Failure -> return result
            is AhuResult.Success -> result.value
        }
        return electricityDeposit.pay(
            ElectricityPayment(
                controller = controller,
                amount = amountYuan,
                password = password,
                room = room.details,
            ),
        )
    }

    suspend fun networkRechargeSnapshot(): AhuResult<NetworkRechargeSnapshot> = networkRecharge.load()

    /** 网费充值：先加载账户快照，再按金额与校园卡密码支付。 */
    suspend fun rechargeNetwork(amountYuan: String, password: String): AhuResult<RechargeReceipt> {
        val snapshot = when (val result = networkRecharge.load()) {
            is AhuResult.Failure -> return result
            is AhuResult.Success -> result.value
        }
        return networkRecharge.pay(snapshot, amountYuan, password)
    }

    suspend fun bathroomAccount(bathroom: String, tel: String): AhuResult<BathroomTelInfo> =
        bathroomDeposit.bathroomInfo(bathroom, tel)

    /** 浴室缴费：账号信息由 [bathroomAccount] 取得，密码只在本次调用内使用。 */
    suspend fun rechargeBathroom(
        bathroom: String,
        amountYuan: String,
        password: String,
        account: com.ahu.ahutong.data.model.Data,
    ): AhuResult<RechargeReceipt> = bathroomDeposit.pay(
        BathroomPayment(
            bathroom = bathroom,
            amount = amountYuan,
            password = password,
            account = account,
        ),
    )

    private fun emptyElectricitySelection(controller: ElectricityController) = RoomSelectionInfo(
        campus = null,
        building = null,
        floor = null,
        room = null,
        controller = controller,
    )

    /**
     * 把旧工程的「周次 + 星期」课表展开成未来若干天的具体日期。
     * 周次优先取教务返回的当前教学周；拿不到时退回本地学期起始日。
     */
    suspend fun scheduleOccurrences(days: Int): AhuResult<List<DatedCourse>> {
        val courses = when (val result = schedule(days)) {
            is AhuResult.Failure -> return result
            is AhuResult.Success -> result.value
        }
        val config = runCatching { CurrentWeekResolver.resolveLocalFirst() }.getOrNull()
            ?: CurrentWeekResolver.defaultConfig()
        val today = LocalDate.now()
        val semesterStart = config.config.startTime?.let { start ->
            java.time.Instant.ofEpochMilli(start.time)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        } ?: today.minusDays((today.dayOfWeek.value - 1).toLong())
        val dated = buildList {
            for (offset in 0 until days.coerceIn(1, 30)) {
                val date = today.plusDays(offset.toLong())
                val week = TeachingWeekPolicy.weekForDate(semesterStart, date)
                courses.filter { course -> course.occursOn(date.dayOfWeek.value, week) }
                    .sortedBy { it.startTime }
                    .forEach { course ->
                        val clock = ScheduleSectionTimes.getCourseClockRange(course).orEmpty()
                        add(
                            DatedCourse(
                                date = date.toString(),
                                weekday = date.dayOfWeek.value,
                                name = course.name.orEmpty(),
                                teacher = course.teacher.orEmpty(),
                                location = course.location.orEmpty(),
                                startTime = clock.substringBefore('-', ""),
                                endTime = clock.substringAfter('-', ""),
                            ),
                        )
                    }
            }
        }
        return AhuResult.Success(dated)
    }

    private fun Course.occursOn(weekday: Int, week: Int): Boolean {
        if (getWeekday() != weekday) return false
        val indexed = weekIndexes
        if (!indexed.isNullOrEmpty()) return week in indexed
        val start = getStartWeek()
        val end = getEndWeek()
        if (start <= 0) return true
        return week in start..end
    }
}

data class DatedCourse(
    val date: String,
    val weekday: Int,
    val name: String,
    val teacher: String,
    val location: String,
    val startTime: String,
    val endTime: String,
)

fun AhuError.toChinese(): String = when (this) {
    AhuError.Network -> "网络连接失败，请检查网络后重试"
    AhuError.Timeout -> "请求超时，请稍后重试"
    is AhuError.Unauthorized -> message.ifBlank { "登录已失效，请重新登录" }
    is AhuError.ProtocolChanged -> detail
    is AhuError.Server -> "校方服务返回错误${if (code > 0) "（$code）" else ""}：$message"
    is AhuError.Unknown -> message
}
