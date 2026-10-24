package com.ahu.ahutong.legacy

import android.content.Context
import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.AppEnvironment
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.data.session.SessionStore
import com.tencent.mmkv.MMKV
import java.time.LocalDate

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
                    persistUser(outcome.user)
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
                persistUser(user)
                LoginResult.Success(user.name.orEmpty())
            }
            is AhuResult.Failure -> LoginResult.Failure(result.error.toChinese())
        }
    }

    fun logout() {
        pendingWebUser = null
        runCatching { AHUCache.clearCurrentUser() }
        runCatching {
            com.ahu.ahutong.data.crawler.manager.CookieManager.cookieJar.clear()
        }
    }

    private fun persistUser(user: User) {
        AHUCache.saveCurrentUser(user)
    }

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
        val dated = buildList {
            for (offset in 0 until days.coerceIn(1, 30)) {
                val date = today.plusDays(offset.toLong())
                val week = if (config.config.isInSemester) {
                    config.config.week + ((date.toEpochDay() - today.toEpochDay()) / 7).toInt()
                } else {
                    config.config.week
                }
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
