package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.model.ycard.CardBalanceRequest
import com.ahu.ahutong.data.crawler.model.ycard.CardPayRequest
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.data.model.CardRechargeBank
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 校园卡充值的三段协议：查卡 → 开单（订单号藏在重定向 URL 里）→ 对单支付。
 * 不做任何重试：支付响应读不出来时，钱可能已经扣了。
 */
class RepositoryCardRechargeSource {

    suspend fun recharge(
        amountYuan: String,
        bank: CardRechargeBank,
    ): AhuResult<CardRechargeOutcome> = withContext(Dispatchers.IO) {
        val info = when (val result = AHURepository.getCardInfo()) {
            is AhuResult.Failure -> return@withContext AhuResult.Failure(result.error)
            is AhuResult.Success -> result.value
        }
        val cardType = info.data.card.firstOrNull()?.accinfo?.firstOrNull()?.type
            ?: return@withContext AhuResult.Failure(
                AhuError.ProtocolChanged("校园卡账户信息缺少卡类型"),
            )

        val orderResponse = when (val result = AHURepository.getOrderThirdData(
            CardBalanceRequest(amountYuan, cardType),
        )) {
            is AhuResult.Failure -> return@withContext AhuResult.Failure(result.error)
            is AhuResult.Success -> result.value
        }
        val orderId = ORDER_ID.find(orderResponse.raw().request.url.toString())
            ?.groupValues?.get(1)
            ?: return@withContext AhuResult.Failure(
                AhuError.ProtocolChanged("上游没有返回订单号"),
            )

        val payResponse = when (val result = AHURepository.pay(CardPayRequest(orderId, bank))) {
            is AhuResult.Failure -> return@withContext AhuResult.Failure(result.error)
            is AhuResult.Success -> result.value
        }
        val pay = payResponse.body()?.use { body ->
            Gson().fromJson(body.string(), PayResponse::class.java)
        } ?: return@withContext AhuResult.Failure(
            AhuError.ProtocolChanged("上游没有返回支付结果"),
        )

        // 与旧工程保持一致：以业务码 200 判定成功。响应里没有 success 字段时 Gson 会落成 false，
        // 若同时要求 success=true，可能把已扣款的订单误报为失败。
        if (pay.code == 200) {
            AhuResult.Success(
                CardRechargeOutcome(
                    orderId = orderId,
                    message = pay.msg.ifBlank { "充值成功" },
                ),
            )
        } else {
            AhuResult.Failure(AhuError.Server(pay.code, pay.msg.ifBlank { "充值失败" }))
        }
    }

    private companion object {
        val ORDER_ID = Regex("[?]orderid=([^&]+)")
    }
}

data class CardRechargeOutcome(
    val orderId: String,
    val message: String,
)
