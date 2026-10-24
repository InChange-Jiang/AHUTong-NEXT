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
)

interface Tool {
    val spec: ToolSpec
    suspend fun execute(arguments: JsonObject): ToolOutput
}

class ToolRegistry(tools: List<Tool>) {
    private val byName = tools.associateBy { it.spec.name }

    val specs: List<ToolSpec> = tools.map { it.spec }

    fun get(name: String): Tool? = byName[name]
}
