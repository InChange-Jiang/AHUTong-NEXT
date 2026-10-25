package com.ahu.ahutong.next.ui.pages

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.next.app.data.NextCampusRepository
import com.ahu.ahutong.next.data.Balance
import com.ahu.ahutong.next.data.BillSummary
import com.ahu.ahutong.next.data.Electricity
import com.ahu.ahutong.next.data.RechargeReceipt
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.EmptyState
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.nav.AppRoute
import com.ahu.ahutong.next.ui.settings.ElectricityRoomDialog
import com.ahu.ahutong.next.ui.theme.NextPalette
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CardPage(
    repository: NextCampusRepository,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    var state by remember { mutableStateOf<LoadState<Balance>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.balance() } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "校园卡",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val balance = (state as LoadState.Ready).value
            FeatureScrollColumn {
                PageCard {
                    Text("账户余额", style = MaterialTheme.typography.labelMedium, color = NextPalette.InkSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("¥", style = MaterialTheme.typography.titleMedium, color = NextPalette.AccentDeep)
                        Text(
                            String.format(Locale.CHINA, "%.2f", balance.yuan),
                            style = MaterialTheme.typography.displaySmall,
                            color = NextPalette.AccentDeep,
                        )
                    }
                    Text(
                        "${balance.cardNo} · 更新于 ${balance.updatedAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
                PageCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftPrimaryButton("付款码", onClick = { showQr = true }, modifier = Modifier.weight(1f))
                        SoftSecondaryButton("充值", onClick = { onNavigate(AppRoute.RechargeCard) }, modifier = Modifier.weight(1f))
                    }
                    SoftSecondaryButton(
                        "账单与消费分析",
                        onClick = { onNavigate(AppRoute.Bills) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "付款码只在本机全屏展示，不会进入对话历史。",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            }
        }
    }
    if (showQr) PaymentQrDialog(onDismiss = { showQr = false })
}

@Composable
private fun PaymentQrDialog(onDismiss: () -> Unit) {
    var payload by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                com.ahu.ahutong.legacy.LegacyCampusApi.paymentQr().requireValue()
            }
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
                .background(NextPalette.Background)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("校园卡付款码", style = MaterialTheme.typography.titleLarge, color = NextPalette.Ink)
                Column(
                    modifier = Modifier
                        .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        loading -> CircularProgressIndicator(color = NextPalette.Accent)
                        error != null -> Text(error!!, color = NextPalette.Danger)
                        payload != null -> QrImage(payload!!)
                    }
                }
                Text(
                    error ?: "请将二维码对准扫码设备；仅用于本人校园卡消费。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftSecondaryButton("刷新", onClick = { reload++ })
                    SoftPrimaryButton("关闭", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun QrImage(payload: String) {
    val bitmap = remember(payload) {
        runCatching {
            val size = 720
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
            }
        }.getOrNull()
    }
    if (bitmap == null) {
        Text("二维码生成失败", color = NextPalette.Danger)
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "校园卡付款码",
            modifier = Modifier.size(260.dp),
        )
    }
}

@Composable
fun BillsPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var state by remember { mutableStateOf<LoadState<BillSummary>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.bills() } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold(
        title = "账单与消费",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        AsyncContent(state, onRetry = { reload++ }) {
            val bills = (state as LoadState.Ready).value
            FeatureScrollColumn {
                PageCard {
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("本月支出", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            Text(
                                "¥${String.format(Locale.CHINA, "%.2f", bills.monthSpend)}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NextPalette.Ink,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("本月充值", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            Text(
                                "¥${String.format(Locale.CHINA, "%.2f", bills.monthIncome)}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NextPalette.Success,
                            )
                        }
                    }
                    Text(
                        "最常去：${bills.topPlace}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NextPalette.AccentDeep,
                    )
                }
                PageCard {
                    PageTitle("消费明细")
                    bills.items.take(30).forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.place, style = MaterialTheme.typography.bodyMedium, color = NextPalette.Ink)
                                Text(item.time, style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            }
                            Text(
                                if (item.amount < 0) "-¥${String.format(Locale.CHINA, "%.2f", -item.amount)}"
                                else "+¥${String.format(Locale.CHINA, "%.2f", item.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.amount < 0) NextPalette.Ink else NextPalette.Success,
                            )
                        }
                        if (index != bills.items.take(30).lastIndex) HorizontalDivider(color = NextPalette.Line)
                    }
                    if (bills.items.isEmpty()) EmptyState("本月还没有流水。")
                }
            }
        }
    }
}

@Composable
fun ElectricityPage(
    repository: NextCampusRepository,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    var state by remember { mutableStateOf<LoadState<Electricity>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var showRoom by remember { mutableStateOf(false) }
    val roomLabel = remember { mutableStateOf(repository.electricityRoomLabel()) }

    LaunchedEffect(reload, roomLabel.value) {
        state = LoadState.Loading
        state = runCatching { withContext(Dispatchers.IO) { repository.electricity() } }
            .fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }

    FeatureScaffold(
        title = "宿舍电费",
        onBack = onBack,
        actions = { TextButton(onClick = { reload++ }) { Text("刷新") } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("电费房间", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                        Text(
                            roomLabel.value ?: "尚未设置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (roomLabel.value == null) NextPalette.Danger else NextPalette.Ink,
                        )
                    }
                    SoftSecondaryButton(
                        if (roomLabel.value == null) "设置房间" else "修改房间",
                        onClick = { showRoom = true },
                    )
                }
            }
        }
        AsyncContent(state, onRetry = { reload++ }) {
            val electricity = (state as LoadState.Ready).value
            FeatureScrollColumn {
                PageCard {
                    Text("剩余电量", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            String.format(Locale.CHINA, "%.1f", electricity.balanceKwh),
                            style = MaterialTheme.typography.displaySmall,
                            color = NextPalette.AccentDeep,
                        )
                        Text(
                            " 度",
                            style = MaterialTheme.typography.titleMedium,
                            color = NextPalette.AccentDeep,
                        )
                    }
                    Text(
                        "预计可用 ${electricity.estimatedDays} 天 · ${electricity.room}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
                PageCard {
                    PageTitle("近七日用电")
                    UsageBars(electricity.dailyUsage)
                }
                PageCard {
                    SoftPrimaryButton(
                        "充值",
                        onClick = { onNavigate(AppRoute.RechargeElectricity) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SoftSecondaryButton(
                        "电费提醒设置",
                        onClick = { onNavigate(AppRoute.Reminders) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (showRoom) {
        ElectricityRoomDialog(
            onDismiss = { showRoom = false },
            onSaved = {
                roomLabel.value = repository.electricityRoomLabel()
                showRoom = false
                reload++
            },
        )
    }
}

@Composable
private fun UsageBars(values: List<Double>) {
    if (values.isEmpty()) {
        Text("暂无用电记录", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
        return
    }
    val max = values.maxOrNull()?.coerceAtLeast(0.1) ?: 1.0
    Row(
        modifier = Modifier.fillMaxWidth().height(90.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((10f + (value / max).toFloat() * 60f).dp)
                        .background(NextPalette.AccentSoft, RoundedCornerShape(6.dp)),
                )
                Text(
                    String.format(Locale.CHINA, "%.1f", value),
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
            }
        }
    }
}

@Composable
fun RechargeCardPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("agricultural") }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RechargeReceipt?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FeatureScaffold("校园卡充值", onBack) {
        FeatureScrollColumn {
            AmountCard(amount, { amount = it })
            PageCard {
                PageTitle("扣款银行卡")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("中国农业银行", onClick = { bank = "agricultural" }, selected = bank == "agricultural")
                    SoftChip("招商银行", onClick = { bank = "cmb" }, selected = bank == "cmb")
                }
                Text(
                    "确认后会立即从所选银行卡扣款；失败不会自动重试。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                SoftPrimaryButton("确认充值", onClick = { confirm = true }, modifier = Modifier.fillMaxWidth())
            }
            result?.let {
                ReceiptCard(it)
            }
        }
    }

    if (confirm) {
        val text = amount.toDoubleOrNull()?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "--"
        ConfirmPaymentDialog(
            title = "确认充值",
            summary = "将从${if (bank == "agricultural") "中国农业银行" else "招商银行"}扣款 ¥$text，充值到校园卡。",
            needsPassword = false,
            submitting = submitting,
            onDismiss = { confirm = false },
            onConfirm = { _ ->
                submitting = true
                error = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { repository.rechargeCard(amount, bank) } }
                        .fold(onSuccess = { result = it }, onFailure = { error = it.message ?: "充值失败" })
                    submitting = false
                    confirm = false
                }
            },
        )
    }
    error?.let { ErrorDialog(it) { error = null } }
}

@Composable
fun RechargeElectricityPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RechargeReceipt?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var roomInfo by remember { mutableStateOf<String?>(null) }
    var showRoom by remember { mutableStateOf(false) }
    var roomReload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val room = remember { repository.electricityRoomLabel() }

    LaunchedEffect(roomReload) {
        roomInfo = runCatching {
            withContext(Dispatchers.IO) {
                com.ahu.ahutong.legacy.LegacyCampusApi.electricityRoomInfo().requireValue().displayInfo
            }
        }.getOrNull()
    }

    FeatureScaffold("宿舍电费充值", onBack) {
        FeatureScrollColumn {
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            room?.let { "充值房间：$it" } ?: "还没有设置电费房间",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (room == null) NextPalette.Danger else NextPalette.Ink,
                        )
                        roomInfo?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                        }
                    }
                    SoftSecondaryButton("更换房间", onClick = { showRoom = true })
                }
            }
            AmountCard(amount, { amount = it })
            PageCard {
                Text(
                    "支付密码为校园卡 6 位密码，只在确认框里输入，不会保存或发给模型。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                SoftPrimaryButton(
                    "确认充值",
                    enabled = room != null,
                    onClick = { confirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            result?.let { ReceiptCard(it) }
        }
    }

    if (confirm) {
        ConfirmPaymentDialog(
            title = "确认电费充值",
            summary = "将从校园卡账户扣款 ¥${amount.ifBlank { "--" }}，充值到 $room。",
            needsPassword = true,
            submitting = submitting,
            onDismiss = { confirm = false },
            onConfirm = { password ->
                submitting = true
                error = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repository.rechargeElectricity(amount, password.orEmpty())
                        }
                    }.fold(onSuccess = { result = it }, onFailure = { error = it.message ?: "电费充值失败" })
                    submitting = false
                    confirm = false
                }
            },
        )
    }
    error?.let { ErrorDialog(it) { error = null } }

    if (showRoom) {
        com.ahu.ahutong.next.ui.settings.ElectricityRoomDialog(
            onDismiss = { showRoom = false },
            onSaved = {
                showRoom = false
                roomReload++
            },
        )
    }
}

@Composable
fun RechargeBathroomPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var bathroom by remember { mutableStateOf("竹园/龙河") }
    var phone by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RechargeReceipt?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var accountInfo by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bathroom, phone) {
        accountInfo = null
        accountError = null
        if (phone.length != 11) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                com.ahu.ahutong.legacy.LegacyCampusApi.bathroomAccount(bathroom, phone).requireValue()
            }
        }.fold(
            onSuccess = { info ->
                val show = info.map?.showData
                accountInfo = if (show != null) {
                    "${show.phone} · 现金 ${show.cashAmount} 元 · 赠送 ${show.giftAmount} 元"
                } else {
                    info.map?.data?.message ?: "已找到浴室账户"
                }
                if (info.map?.data == null) accountError = info.message ?: "未查询到浴室账户"
            },
            onFailure = { accountError = it.message ?: "浴室账户查询失败" },
        )
    }

    FeatureScaffold("浴室缴费", onBack) {
        FeatureScrollColumn {
            PageCard {
                PageTitle("浴室")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("竹园/龙河", onClick = { bathroom = "竹园/龙河" }, selected = bathroom == "竹园/龙河")
                    SoftChip("桔园/蕙园", onClick = { bathroom = "桔园/蕙园" }, selected = bathroom == "桔园/蕙园")
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    label = { Text("浴室账户手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                accountInfo?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = NextPalette.AccentDeep)
                }
                accountError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = NextPalette.Danger)
                }
            }
            AmountCard(amount, { amount = it })
            PageCard {
                Text(
                    "支付密码为校园卡 6 位密码，只在确认框里输入，不会保存或发给模型。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                SoftPrimaryButton(
                    "确认缴费",
                    enabled = phone.length == 11 && accountError == null,
                    onClick = { confirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            result?.let { ReceiptCard(it) }
        }
    }

    if (confirm) {
        ConfirmPaymentDialog(
            title = "确认浴室缴费",
            summary = "将从校园卡账户扣款 ¥${amount.ifBlank { "--" }}，充值到「$bathroom」的 $phone。",
            needsPassword = true,
            submitting = submitting,
            onDismiss = { confirm = false },
            onConfirm = { password ->
                submitting = true
                error = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repository.rechargeBathroom(bathroom, phone, amount, password.orEmpty())
                        }
                    }.fold(onSuccess = { result = it }, onFailure = { error = it.message ?: "浴室缴费失败" })
                    submitting = false
                    confirm = false
                }
            },
        )
    }
    error?.let { ErrorDialog(it) { error = null } }
}

@Composable
fun RechargeNetworkPage(repository: NextCampusRepository, onBack: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var accountSummary by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                com.ahu.ahutong.legacy.LegacyCampusApi.networkRechargeSnapshot().requireValue()
            }
        }.onSuccess { snapshot ->
            accountSummary = buildString {
                snapshot.paymentData.account?.takeIf { it.isNotBlank() }?.let { append("充值账号 $it · ") }
                append("余额 ${snapshot.paymentData.balance ?: "--"} 元")
                snapshot.feeItem.maxMoney?.takeIf { it.isNotBlank() }?.let { append(" · 单次最高 $it 元") }
            }
        }
    }
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RechargeReceipt?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FeatureScaffold("网费充值", onBack) {
        FeatureScrollColumn {
            accountSummary?.let {
                PageCard {
                    Text("网费账户", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = NextPalette.Ink)
                }
            }
            AmountCard(amount, { amount = it }, listOf("5", "15", "25", "50", "100"))
            PageCard {
                Text(
                    "单次最高 999 元；支付密码为校园卡 6 位密码，只在确认框里输入，不会保存或发给模型。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.InkSecondary,
                )
                SoftPrimaryButton(
                    "确认充值",
                    enabled = amount.toDoubleOrNull()?.let { it in 0.01..999.0 } == true,
                    onClick = { confirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            result?.let { ReceiptCard(it) }
        }
    }

    if (confirm) {
        ConfirmPaymentDialog(
            title = "确认网费充值",
            summary = "将从校园卡账户扣款 ¥${amount.ifBlank { "--" }}，充值到校园网账户。",
            needsPassword = true,
            submitting = submitting,
            onDismiss = { confirm = false },
            onConfirm = { password ->
                submitting = true
                error = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { repository.rechargeNetwork(amount, password.orEmpty()) }
                    }.fold(onSuccess = { result = it }, onFailure = { error = it.message ?: "网费充值失败" })
                    submitting = false
                    confirm = false
                }
            },
        )
    }
    error?.let { ErrorDialog(it) { error = null } }
}

@Composable
private fun AmountCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    quickAmounts: List<String> = listOf("10", "20", "50", "100"),
) {
    PageCard {
        PageTitle("充值金额")
        OutlinedTextField(
            value = amount,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) onAmountChange(input)
            },
            label = { Text("金额（元）") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickAmounts.forEach { quick ->
                SoftChip("¥$quick", onClick = { onAmountChange(quick) }, selected = amount == quick)
            }
        }
    }
}

@Composable
private fun ReceiptCard(receipt: RechargeReceipt) {
    PageCard {
        Text("充值成功", style = MaterialTheme.typography.titleSmall, color = NextPalette.Success)
        Text(
            "¥${String.format(Locale.CHINA, "%.2f", receipt.amountYuan)} · ${receipt.target}",
            style = MaterialTheme.typography.headlineSmall,
            color = NextPalette.Ink,
        )
        Text(
            "${receipt.channel} · ${receipt.message}",
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
        )
        Text(
            "订单号 ${receipt.orderId}",
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
        )
    }
}

@Composable
private fun ConfirmPaymentDialog(
    title: String,
    summary: String,
    needsPassword: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = NextPalette.Ink)
                if (needsPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { input -> password = input.filter(Char::isDigit).take(6) },
                        label = { Text("校园卡支付密码（6 位）") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "密码只在本机使用，不保存、不进入模型上下文。",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                }
                Text(
                    "失败不会自动重试，请先核对余额。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NextPalette.Danger,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && (!needsPassword || password.length == 6),
                onClick = {
                    val secret = password.takeIf { needsPassword }
                    password = ""
                    onConfirm(secret)
                },
            ) { Text(if (submitting) "处理中…" else "确认扣款") }
        },
        dismissButton = { TextButton(enabled = !submitting, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作未完成") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
