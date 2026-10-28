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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.ui.components.CampusQrImage
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.pages.requireValue
import com.ahu.ahutong.next.ui.theme.NextPalette
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
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { LegacyCampusApi.paymentQr().requireValue() }
        }.fold(
            onSuccess = { payload = it },
            onFailure = { error = it.message ?: "付款码获取失败" },
        )
        loading = false
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

                    Column(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(22.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when {
                            loading -> CircularProgressIndicator(
                                modifier = Modifier.padding(56.dp),
                                color = NextPalette.Accent,
                            )

                            error != null -> Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    error.orEmpty(),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NextPalette.Danger,
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(onClick = { reload++ }) { Text("重试") }
                            }

                            payload != null -> CampusQrImage(
                                payload = payload.orEmpty(),
                                size = 236.dp,
                            )
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
