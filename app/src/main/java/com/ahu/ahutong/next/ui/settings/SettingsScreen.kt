package com.ahu.ahutong.next.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.BuildConfig
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.SettingsRepository
import com.ahu.ahutong.next.ui.components.SoftBackground
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.SoftSurface
import com.ahu.ahutong.next.ui.components.SoftTextField
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modelLabel: String,
    historyCount: Int,
    displayScale: Float,
    onDisplayScaleChange: (Float) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenLab: () -> Unit,
    suggestionsEnabled: Boolean,
    onSuggestionsChange: (Boolean) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf(settings.baseUrl()) }
    var model by rememberSaveable { mutableStateOf(settings.model()) }
    var hasKey by remember { mutableStateOf(!settings.apiKey().isNullOrBlank()) }
    var searchEnabled by remember { mutableStateOf(settings.searchEnabled()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf(LegacyCampusApi.currentUserName()) }
    var showLogin by remember { mutableStateOf(false) }
    var chaoxingConnected by remember { mutableStateOf(LegacyCampusApi.hasChaoxingSession()) }
    var showChaoxingLogin by remember { mutableStateOf(false) }
    var showElectricityRoom by remember { mutableStateOf(false) }
    var electricityRoomLabel by remember {
        mutableStateOf(LegacyCampusApi.savedElectricityRoom()?.displayLabel())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SoftBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                SettingsTopBar(onBack)

                SettingsCard {
                    SectionTitle("安大通账号")
                    if (accountName.isNullOrBlank()) {
                        Text(
                            "未登录时，课程、成绩等查询使用演示数据。登录本科教务账号后切换为真实数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NextPalette.InkSecondary,
                        )
                        SoftPrimaryButton(
                            text = "登录",
                            onClick = { showLogin = true },
                        )
                    } else {
                        Text(
                            "已登录：$accountName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NextPalette.Ink,
                        )
                        Text(
                            "课表、成绩、考试安排来自教务系统；一卡通余额、账单、电费、网费与充值来自校园卡系统。电费/浴室/网费充值的密码只在确认卡里输入。",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                        SoftSecondaryButton(
                            text = "账号与切换",
                            onClick = onOpenAccounts,
                        )
                        SoftSecondaryButton(
                            text = "退出登录",
                            onClick = {
                                LegacyCampusApi.logout()
                                accountName = null
                                scope.launch { snackbar.showSnackbar("已退出登录") }
                            },
                        )
                    }

                    HorizontalDivider(color = NextPalette.Line)

                    Text(
                        "学习通（作业）",
                        style = MaterialTheme.typography.titleSmall,
                        color = NextPalette.Ink,
                    )
                    Text(
                        if (chaoxingConnected) {
                            "已连接学习通，作业查询使用真实数据。"
                        } else {
                            "未连接时作业使用演示数据。学习通是独立账号。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPalette.InkSecondary,
                    )
                    if (chaoxingConnected) {
                        SoftSecondaryButton(
                            text = "重新登录学习通",
                            onClick = { showChaoxingLogin = true },
                        )
                    } else {
                        SoftSecondaryButton(
                            text = "连接学习通",
                            onClick = { showChaoxingLogin = true },
                        )
                    }
                }

                Spacer(Modifier.padding(top = 14.dp))

                SettingsCard {
                    SectionTitle("模型")
                    Text(
                        "自带 API Key（BYOK）。Key 只用 Android Keystore 加密保存在本机，不会上传到安大通服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                    SoftTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = "API Key",
                        placeholder = if (hasKey) "已保存；留空则不修改" else "sk-...",
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SoftTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = "Base URL",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SoftTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = "模型名",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "联网搜索",
                                style = MaterialTheme.typography.titleSmall,
                                color = NextPalette.Ink,
                            )
                            Text(
                                if (hasKey) {
                                    "通过 DeepSeek 搜索获取实时网页信息"
                                } else {
                                    "配置 API Key 后生效"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                        }
                        Switch(
                            checked = searchEnabled,
                            onCheckedChange = {
                                searchEnabled = it
                                settings.setSearchEnabled(it)
                            },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftPrimaryButton(
                            text = "保存",
                            onClick = {
                                settings.save(
                                    baseUrl = baseUrl,
                                    model = model,
                                    apiKey = apiKeyInput.trim().takeIf { it.isNotBlank() },
                                )
                                hasKey = !settings.apiKey().isNullOrBlank()
                                apiKeyInput = ""
                                onSaved()
                                scope.launch { snackbar.showSnackbar("已保存") }
                            },
                        )
                        SoftSecondaryButton(
                            text = "清除 Key",
                            onClick = {
                                settings.clearApiKey()
                                hasKey = false
                                onSaved()
                                scope.launch { snackbar.showSnackbar("已清除 API Key") }
                            },
                        )
                    }

                    Text(
                        "当前模式：$modelLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = NextPalette.AccentDeep,
                    )
                }

                Spacer(Modifier.padding(top = 14.dp))

                SettingsCard {
                    SectionTitle("显示")
                    Text(
                        "调整聊天、卡片与设置页的整体大小。小屏手机建议 88% - 95%，大屏可以适当放大。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "小",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                        Slider(
                            value = displayScale,
                            onValueChange = { onDisplayScaleChange((it * 100).roundToInt() / 100f) },
                            valueRange = SettingsRepository.MIN_DISPLAY_SCALE..
                                SettingsRepository.MAX_DISPLAY_SCALE,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = NextPalette.Accent,
                                activeTrackColor = NextPalette.Accent,
                                inactiveTrackColor = NextPalette.AccentSoft,
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent,
                            ),
                        )
                        Text(
                            "大",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "当前：${(displayScale * 100).roundToInt()}%",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = NextPalette.AccentDeep,
                        )
                        TextButton(onClick = { onDisplayScaleChange(1f) }) {
                            Text("恢复标准")
                        }
                    }
                }

                Spacer(Modifier.padding(top = 14.dp))

                SettingsCard {
                    SectionTitle("偏好")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "显示快捷建议",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NextPalette.Ink,
                            )
                            Text(
                                "空会话时展示作业、课表、成绩等推荐问句。",
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                        }
                        Switch(
                            checked = suggestionsEnabled,
                            onCheckedChange = onSuggestionsChange,
                        )
                    }
                    SoftSecondaryButton(
                        text = "提醒设置",
                        onClick = onOpenReminders,
                    )
                }

                Spacer(Modifier.padding(top = 14.dp))

                SettingsCard {
                    SectionTitle("应用")
                    SoftSecondaryButton(
                        text = "应用更新",
                        onClick = onOpenUpdates,
                    )
                    SoftSecondaryButton(
                        text = "关于安大通 NEXT",
                        onClick = onOpenAbout,
                    )
                }

                Spacer(Modifier.padding(top = 14.dp))

                SettingsCard {
                    SectionTitle("宿舍电费")
                    Text(
                        electricityRoomLabel?.let { "当前房间：$it" }
                            ?: "还没有设置房间。查询电费前先选一次，之后会一直记住。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                    SoftSecondaryButton(
                        text = if (electricityRoomLabel == null) "选择房间" else "修改房间",
                        onClick = { showElectricityRoom = true },
                    )
                }

                Spacer(Modifier.padding(top = 14.dp))

                SettingsCard {
                    SectionTitle("数据")
                    Text(
                        "单会话本地历史，共 $historyCount 条。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                    SoftSecondaryButton(
                        text = "清除聊天记录",
                        onClick = { showClearDialog = true },
                    )
                }

                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.padding(top = 14.dp))
                    SettingsCard {
                        SectionTitle("开发者")
                        SoftSecondaryButton(
                            text = "工具工作台",
                            onClick = onOpenLab,
                        )
                        Text(
                            "直接调用工具，绕过模型与确认卡，仅用于调试。",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }

                Spacer(Modifier.padding(bottom = 24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(18.dp),
        ) { data ->
            SoftSurface(
                shape = RoundedCornerShape(18.dp),
                elevation = 14.dp,
            ) {
                Text(
                    data.visuals.message,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NextPalette.Ink,
                )
            }
        }
    }

    if (showLogin) {
        AccountLoginDialog(
            onDismiss = { showLogin = false },
            onSuccess = { name ->
                accountName = name.ifBlank { "已登录" }
                showLogin = false
                scope.launch { snackbar.showSnackbar("登录成功，切换到真实数据") }
            },
        )
    }

    if (showChaoxingLogin) {
        ChaoxingLoginDialog(
            onDismiss = { showChaoxingLogin = false },
            onSuccess = {
                chaoxingConnected = true
                showChaoxingLogin = false
                scope.launch { snackbar.showSnackbar("学习通已连接") }
            },
        )
    }

    if (showElectricityRoom) {
        ElectricityRoomDialog(
            onDismiss = { showElectricityRoom = false },
            onSaved = { label ->
                electricityRoomLabel = label
                scope.launch { snackbar.showSnackbar("电费房间已保存") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除聊天记录？") },
            text = { Text("本地历史会被删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                        scope.launch { snackbar.showSnackbar("聊天记录已清除") }
                    },
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftIconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_iconpark_left),
                contentDescription = "返回",
                tint = NextPalette.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            "设置",
            style = MaterialTheme.typography.titleLarge,
            color = NextPalette.Ink,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    SoftSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = NextPalette.Ink,
    )
}
