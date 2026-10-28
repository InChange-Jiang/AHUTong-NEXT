package com.ahu.ahutong.next.app.data

data class ModelPreset(
    val label: String,
    val baseUrl: String,
    val model: String,
)

/** 官方 OpenAI 兼容入口；模型名以各厂商 2026-10 文档为准，可手动改。 */
val modelPresets = listOf(
    ModelPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-flash"),
    ModelPreset("Kimi", "https://api.moonshot.cn/v1", "kimi-k3"),
    ModelPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/", "glm-5.3"),
    ModelPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.8-max"),
    ModelPreset("OpenAI", "https://api.openai.com/v1", "gpt-5.5"),
    ModelPreset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-3.8-flash"),
)
