package com.ahu.ahutong.next.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.ui.components.CampusQrImage
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.pages.requireValue
import com.ahu.ahutong.next.ui.theme.NextPalette
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 对话页左下角入口弹出的校园卡付款码卡片。 */
@Composable
fun PaymentQrCardDialog(
    onDismiss: () -> Unit,
    onRecharge: () -> Unit,
    onBills: () -> Unit,
) {
    var payload by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var balance by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(reload) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) { LegacyCampusApi.paymentQr().requireValue() }
        }.fold(
            onSuccess = { payload = it },
            onFailure = { error = it.message ?: "付款码获取失败" },
        )
    }

    LaunchedEffect(reload) {
        balance = runCatching {
            withContext(Dispatchers.IO) { LegacyCampusApi.cardMoney().requireValue().balance }
        }.getOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = NextPalette.Background,
                shadowElevation = 20.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "校园卡付款码",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NextPalette.Ink,
                            )
                        }
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "当前余额",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            balance?.let { String.format(Locale.CHINA, "¥ %.2f", it) } ?: "¥ --",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NextPalette.AccentDeep,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(22.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when {
                            payload != null -> CampusQrImage(
                                payload = payload.orEmpty(),
                                size = QR_SIZE,
                            )

                            error != null -> QrPlaceholder(
                                message = error.orEmpty(),
                                onRetry = { reload++ },
                            )

                            else -> QrPlaceholder()
                        }
                    }

                    TextButton(onClick = { reload++ }) { Text("刷新二维码") }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SoftPrimaryButton(
                            text = "充值",
                            onClick = onRecharge,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(2.dp))
                        SoftSecondaryButton(
                            text = "账单",
                            onClick = onBills,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private val QR_SIZE = 236.dp

/** 与二维码等大的占位，加载中或失败时都不会让卡片高度跳变。 */
@Composable
private fun QrPlaceholder(
    message: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(QR_SIZE)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF4F3EF)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_iconpark_qr),
                contentDescription = null,
                tint = Color(0xFFB9B5AB),
                modifier = Modifier.size(if (message == null) 64.dp else 44.dp),
            )
            if (message == null) {
                Text(
                    "正在获取付款码",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A857C),
                )
            } else {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.Danger,
                    textAlign = TextAlign.Center,
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}
