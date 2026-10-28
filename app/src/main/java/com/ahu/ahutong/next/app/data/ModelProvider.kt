package com.ahu.ahutong.next.app.data

/** 由 Base URL 识别厂商；不依赖用户当前选了哪个预设。 */
data class ModelProviderInfo(
    val label: String,
    val supportsWebSearch: Boolean,
)

fun providerFor(baseUrl: String): ModelProviderInfo {
    val url = baseUrl.lowercase()
    return when {
        "deepseek.com" in url -> ModelProviderInfo("DeepSeek", supportsWebSearch = true)
        "moonshot.cn" in url -> ModelProviderInfo("Kimi", supportsWebSearch = false)
        "bigmodel.cn" in url -> ModelProviderInfo("智谱 GLM", supportsWebSearch = false)
        "dashscope.aliyuncs.com" in url -> ModelProviderInfo("通义千问", supportsWebSearch = false)
        "openai.com" in url -> ModelProviderInfo("OpenAI", supportsWebSearch = false)
        "googleapis.com" in url -> ModelProviderInfo("Gemini", supportsWebSearch = false)
        else -> ModelProviderInfo("自定义模型", supportsWebSearch = false)
    }
}

fun modelLabel(baseUrl: String, model: String, hasKey: Boolean): String =
    if (!hasKey) "未配置模型" else "${providerFor(baseUrl).label} · $model"
