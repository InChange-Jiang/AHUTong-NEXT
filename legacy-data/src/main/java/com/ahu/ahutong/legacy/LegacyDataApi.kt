package com.ahu.ahutong.legacy

import android.content.Context
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.AppEnvironment
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.common.map
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Card
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.session.DefaultAhuSession
import com.ahu.ahutong.data.session.SecureCredentialVault
import com.ahu.ahutong.data.toAhuError
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Raw, model-preserving facade over the frozen legacy data layer.
 *
 * This is the exact contract requested for the data extraction: the old
 * `com.ahu.ahutong.data.model.*` types are returned as-is (the new app maps them later), and
 * every call goes through the pure-Kotlin [com.ahu.ahutong.data.crawler.CrawlerDataSource].
 * The Rust JNI `.so` (arm64-only) is never loaded.
 *
 * [LegacyCampusApi] in the same package is the app-facing, view-model-shaped wrapper built by
 * the integration work; it deliberately returns friendly strings. Both share the same session,
 * storage and crawler; use this object when you need the original [AhuResult] / LoginOutcome
 * vocabulary.
 */
object LegacyDataApi {

    private data class PendingWebLogin(val user: User, val password: String)

    @Volatile
    private var initialized = false

    @Volatile
    private var pendingWebLogin: PendingWebLogin? = null

    /**
     * Installs the application context, disables the old mock flag, initializes Chaoxing's
     * SharedPreferences store and forces the crawler data source. Idempotent.
     *
     * Must run before any other call. If [LegacyCampusApi.init] already installed the
     * environment, this object auto-detects it and finishes the remaining setup lazily.
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            AppEnvironmentHolder.install(object : AppEnvironment {
                override val context: Context = appContext
            })
            Store.init(appContext)
            AHUCache.setMockData(false)
            AHURepository.useCrawlerDataSource()
            AHURepository.initializeDataSource(false)
            initialized = true
        }
    }

    /**
     * Crawler login: Wisdom CAS -> undergraduate / postgraduate academic portal.
     *
     * On [LoginOutcome.Success] the password is stored in the Keystore-backed vault and the
     * user in the session store, exactly where the old LoginViewModel did it.
     * On [LoginOutcome.JwxtWebVerificationRequired] the account stays only in memory until
     * [importWebLoginCookies] succeeds.
     */
    suspend fun login(username: String, password: String): AhuResult<LoginOutcome> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            AHURepository.useCrawlerDataSource()
            pendingWebLogin = null
            val result = DefaultAhuSession.signIn(username.trim(), password)
            when (val outcome = result.valueOrNull()) {
                is LoginOutcome.Success -> {
                    persistAuthenticatedUser(outcome.user, password)
                    result
                }

                is LoginOutcome.JwxtWebVerificationRequired -> {
                    pendingWebLogin = PendingWebLogin(outcome.user, password)
                    result
                }

                null -> result
            }
        }

    /**
     * Imports the Cookie JSON exported by the old WebView flow.
     *
     * A successful import also persists a pending account from
     * [LoginOutcome.JwxtWebVerificationRequired] and confirms the session.
     */
    suspend fun importWebLoginCookies(cookiesJson: String): AhuResult<Unit> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val result = AHURepository.importWebLoginCookies(cookiesJson)
            if (result.isFailure) return@withContext result

            val pending = pendingWebLogin
            if (pending != null) {
                try {
                    persistAuthenticatedUser(pending.user, pending.password)
                    DefaultAhuSession.completeWebVerification()
                    pendingWebLogin = null
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    return@withContext AhuResult.Failure(error.toAhuError())
                }
            }
            AhuResult.Success(Unit)
        }

    /** Current-semester timetable, refreshed from the academic portal. */
    suspend fun schedule(): AhuResult<List<Course>> = withContext(Dispatchers.IO) {
        ensureInitialized()
        AHURepository.useCrawlerDataSource()
        AHURepository.refreshScheduleCache().map { it.schedule }
    }

    /**
     * Grades in the old aggregate model: [Grade.termGradeList] holds one entry per term.
     * The legacy repository returns one aggregate, so this cannot honestly be a flat
     * `List<Grade>`; use [gradeTerms] for the per-term list.
     */
    suspend fun grades(): AhuResult<Grade> = withContext(Dispatchers.IO) {
        ensureInitialized()
        AHURepository.useCrawlerDataSource()
        AHURepository.getGrade(isRefresh = true)
    }

    /** Flattened [grades] view: one [Grade.TermGradeListBean] per term. */
    suspend fun gradeTerms(): AhuResult<List<Grade.TermGradeListBean>> = grades().map {
        it.termGradeList.orEmpty()
    }

    /** Campus-card balance, preferring the realtime wallet endpoint. */
    suspend fun cardMoney(): AhuResult<Card> = withContext(Dispatchers.IO) {
        ensureInitialized()
        AHURepository.useCrawlerDataSource()
        AHURepository.getCardMoney(preferRealtime = true)
    }

    /** Exam schedule for the given student identity, refreshed from the portal. */
    suspend fun exams(studentId: String, studentName: String): AhuResult<List<Exam>> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            AHURepository.useCrawlerDataSource()
            AHURepository.getExamInfo(
                isRefresh = true,
                studentID = studentId,
                studentName = studentName
            )
        }

    /**
     * Chaoxing (学习通) login. This is a separate account from the AHU CAS login.
     * The cookie is persisted by [com.ahu.ahutong.data.xuexiaotong.ChaoxingApi]; the
     * credential pair is stored with the Keystore-backed
     * [com.ahu.ahutong.data.xuexiaotong.CredentialCrypto] for silent relogin.
     */
    suspend fun loginChaoxing(account: String, password: String): AhuResult<Unit> =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            try {
                LegacyChaoxingSession.loginByPassword(account, password)
                AhuResult.Success(Unit)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                AhuResult.Failure(error.toAhuError())
            }
        }

    /**
     * Fetches all Chaoxing assignments (作业) for the current session.
     *
     * Requires a previous [loginChaoxing]; the sync walks every course and throttles
     * requests, so it can take a while. Without a session it returns
     * [AhuError.Unauthorized] instead of cached/fabricated data.
     */
    suspend fun assignments(): AhuResult<List<Work>> = withContext(Dispatchers.IO) {
        ensureInitialized()
        if (!LegacyChaoxingSession.hasSession()) {
            return@withContext AhuResult.Failure(
                AhuError.Unauthorized("学习通未登录，请先调用 loginChaoxing")
            )
        }
        try {
            AhuResult.Success(LegacyChaoxingSession.syncWorks { _, _, _ -> })
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            AhuResult.Failure(error.toAhuError())
        }
    }

    /**
     * Auto-finishes setup when `LegacyCampusApi.init` already installed the environment,
     * so the two facades can coexist without ordering surprises.
     */
    private fun ensureInitialized() {
        if (initialized) return
        val installedContext = runCatching { AppEnvironmentHolder.context() }.getOrNull()
        if (installedContext != null) {
            init(installedContext)
            return
        }
        error("LegacyDataApi.init(context) must be called before using the legacy data layer")
    }

    private fun persistAuthenticatedUser(user: User, password: String) {
        // Same order as the old LoginViewModel: never report success before both writes land.
        SecureCredentialVault.saveWisdomPassword(password)
        AHUCache.saveCurrentUser(user)
    }
}
