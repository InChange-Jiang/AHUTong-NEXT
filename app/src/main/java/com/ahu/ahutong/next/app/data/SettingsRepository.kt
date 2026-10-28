package com.ahu.ahutong.next.app.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("next_settings", Context.MODE_PRIVATE)
    private val secrets = SecretStore(appContext)

    fun apiKey(): String? = secrets.get()

    fun baseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun model(): String {
        val saved = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        val legacyDeepSeek = saved == "deepseek-flash" || saved == "deepseek-chat" || saved == "deepseek-reasoner"
        return if (legacyDeepSeek && baseUrl().contains("deepseek.com")) DEFAULT_MODEL else saved
    }

    fun searchEnabled(): Boolean = prefs.getBoolean(KEY_SEARCH_ENABLED, true)

    /**
     * 全局显示大小。默认按屏幕宽度给一个底：小屏更紧凑，大屏略放大。
     * 1.0 为标准；范围限制在 0.80 - 1.20，避免极端值把布局打散。
     */
    fun displayScale(): Float {
        val saved = prefs.getFloat(KEY_DISPLAY_SCALE, -1f)
        if (saved > 0f) return saved.coerceIn(MIN_DISPLAY_SCALE, MAX_DISPLAY_SCALE)
        val widthDp = appContext.resources.configuration.screenWidthDp
        return when {
            widthDp in 1..360 -> 0.88f
            widthDp in 361..399 -> 0.92f
            widthDp >= 600 -> 1.06f
            else -> 1f
        }
    }

    fun setDisplayScale(scale: Float) {
        prefs.edit()
            .putFloat(KEY_DISPLAY_SCALE, scale.coerceIn(MIN_DISPLAY_SCALE, MAX_DISPLAY_SCALE))
            .apply()
    }

    fun setSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH_ENABLED, enabled).apply()
    }

    fun suggestionsEnabled(): Boolean = prefs.getBoolean(KEY_SUGGESTIONS, true)

    fun setSuggestionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUGGESTIONS, enabled).apply()
    }

    fun personality(): AssistantPersonality =
        AssistantPersonality.fromId(prefs.getString(KEY_PERSONALITY, AssistantPersonality.STANDARD.id))

    fun setPersonality(personality: AssistantPersonality) {
        prefs.edit().putString(KEY_PERSONALITY, personality.id).apply()
    }

    /**
     * apiKey 为 null 表示保留现有 Key；空字符串表示清除。
     */
    fun save(baseUrl: String, model: String, apiKey: String?) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/'))
            .putString(KEY_MODEL, model.ifBlank { DEFAULT_MODEL })
            .apply()
        if (apiKey != null) secrets.put(apiKey)
    }

    fun clearApiKey() = secrets.put(null)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_SEARCH_ENABLED = "search_enabled"
        private const val KEY_DISPLAY_SCALE = "display_scale"
        private const val KEY_SUGGESTIONS = "quick_suggestions"
        private const val KEY_PERSONALITY = "assistant_personality"
        const val MIN_DISPLAY_SCALE = 0.80f
        const val MAX_DISPLAY_SCALE = 1.20f
    }
}
