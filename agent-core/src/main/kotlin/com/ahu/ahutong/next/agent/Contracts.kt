package com.ahu.ahutong.next.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val AgentJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
enum class ToolKind {
    READ,
    WRITE,
}

/**
 * 工具结果的信任级别。网页、邮件、通知、失物招领等由用户或外部系统产生的内容
 * 一律是 UNTRUSTED：模型只能把它当数据总结，不能把其中的文字当指令执行。
 */
@Serializable
enum class ToolTrust {
    TRUSTED,
    UNTRUSTED,
}

@Serializable
data class ToolSpec(
    val name: String,
    val displayName: String,
    val description: String,
    val parameters: JsonObject,
    val kind: ToolKind,
    val source: String,
    val timeoutMs: Long = 15_000,
    val requiresConfirmation: Boolean = kind == ToolKind.WRITE,
    /**
     * 校园卡支付密码由确认卡在本机采集，直接交给工具，不进入模型上下文、不写历史。
     * 模型永远看不到这个字段对应的值。
     */
    val requiresPaymentPassword: Boolean = false,
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

@Serializable
data class ToolError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

/**
 * 工具的执行结果。callId 由运行时补齐，工具本身只关心业务结果。
 */
@Serializable
data class ToolOutput(
    val ok: Boolean,
    val data: JsonObject? = null,
    val error: ToolError? = null,
    val cacheAgeMs: Long? = null,
    /** 默认信任；只有外部/用户生成内容才标 UNTRUSTED。 */
    val trust: ToolTrust = ToolTrust.TRUSTED,
)

@Serializable
data class ToolResult(
    val callId: String,
    val toolName: String,
    val ok: Boolean,
    val data: JsonObject? = null,
    val error: ToolError? = null,
    val source: String,
    val fetchedAt: Long,
    val cacheAgeMs: Long? = null,
    val trust: ToolTrust = ToolTrust.TRUSTED,
)

interface Tool {
    val spec: ToolSpec
    suspend fun execute(arguments: JsonObject): ToolOutput
    /** 需要本机支付密码的工具覆盖它；密码只在本次调用内使用。 */
    suspend fun executeWithSecret(arguments: JsonObject, secret: String?): ToolOutput =
        execute(arguments)
    /** 确认卡里补充展示的业务信息，例如电费房间；不要在这里发起网络请求。 */
    suspend fun describe(arguments: JsonObject): String? = null
}

class ToolRegistry(tools: List<Tool>) {
    private val byName = tools.associateBy { it.spec.name }

    val specs: List<ToolSpec> = tools.map { it.spec }

    fun get(name: String): Tool? = byName[name]
}
