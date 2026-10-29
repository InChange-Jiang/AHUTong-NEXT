package com.ahu.ahutong.next.agent

import kotlinx.serialization.json.JsonObject

/**
 * 写操作的本地幂等护栏。运行时在确认后、执行前登记 PENDING，执行后登记结果；
 * 如果上一次相同操作的结果未知，下一次相同操作会被拦下，避免超时重试导致重复扣款。
 *
 * 实现不保存参数原文和支付密码，只保存由工具名 + 参数（不含密码）计算出的摘要。
 */
interface WriteJournal {
    /** 返回非 null 表示应拦截，并把这个说明交给模型/用户。 */
    suspend fun check(toolName: String, arguments: JsonObject): String?

    suspend fun begin(toolName: String, arguments: JsonObject)

    suspend fun finish(toolName: String, arguments: JsonObject, result: ToolResult)
}
