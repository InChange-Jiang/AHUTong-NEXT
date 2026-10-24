package com.ahu.ahutong.next.agent

import kotlinx.coroutines.CompletableDeferred

/**
 * 写操作的暂停点。运行时创建 deferred 并挂起，UI 通过 approve/reject 放行。
 */
class ApprovalGate {
    private var pending: CompletableDeferred<Boolean>? = null

    fun prepare(): CompletableDeferred<Boolean> {
        pending?.cancel()
        return CompletableDeferred<Boolean>().also { pending = it }
    }

    fun approve() {
        pending?.complete(true)
    }

    fun reject() {
        pending?.complete(false)
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
