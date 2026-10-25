package com.ahu.ahutong.next.agent

import kotlinx.coroutines.CompletableDeferred

data class ApprovalDecision(
    val approved: Boolean,
    /** 只在需要支付密码的确认里出现；不写入历史，也不进入模型上下文。 */
    val paymentPassword: String? = null,
)

/**
 * 写操作的暂停点。运行时创建 deferred 并挂起，UI 通过 approve/reject 放行。
 */
class ApprovalGate {
    private var pending: CompletableDeferred<ApprovalDecision>? = null

    fun prepare(): CompletableDeferred<ApprovalDecision> {
        pending?.cancel()
        return CompletableDeferred<ApprovalDecision>().also { pending = it }
    }

    fun approve(paymentPassword: String? = null) {
        pending?.complete(ApprovalDecision(approved = true, paymentPassword = paymentPassword))
    }

    fun reject() {
        pending?.complete(ApprovalDecision(approved = false))
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
