package com.ahu.ahutong.legacy

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work

/**
 * Minimal production adapter for the Chaoxing (学习通) [ChaoxingSession] port.
 *
 * The old app kept this adapter next to its UI (`ui/state/ChaoxingAdapters.kt`); the copied
 * data module only had the port, so it is reproduced here without any UI/Hilt dependency.
 * Cookie persistence and Keystore-backed credential storage are the same as before.
 */
internal object LegacyChaoxingSession : ChaoxingSession {

    private val api by lazy { ChaoxingApi(AppEnvironmentHolder.context()) }

    override fun hasSession(): Boolean = api.hasSession()

    override fun cookieHeader(): String = Store.getCookie()

    override suspend fun loginByPassword(account: String, password: String) {
        api.loginByPassword(account, password)
        // Silent relogin reads this pair back through CredentialCrypto (Android Keystore).
        Store.saveCredential(account, password)
    }

    override fun clearSession() = api.clearSession()

    override suspend fun silentRelogin() {
        api.silentRelogin()
    }

    override suspend fun syncWorks(onProgress: (Int, Int, String) -> Unit): List<Work> =
        api.syncAllWorks(object : ChaoxingApi.ProgressListener {
            override fun onProgress(done: Int, total: Int, message: String) {
                onProgress(done, total, message)
            }
        })

    override suspend fun syncCourseProgress(
        onProgress: (Int, Int, String) -> Unit
    ): List<CourseProgress> =
        api.syncCourseProgress(object : ChaoxingApi.ProgressListener {
            override fun onProgress(done: Int, total: Int, message: String) {
                onProgress(done, total, message)
            }
        })
}
