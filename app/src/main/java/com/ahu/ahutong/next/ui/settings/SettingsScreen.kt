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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

@Composable
fun SettingsScreen(
    modelLabel: String,
    historyCount: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenLab: () -> Unit,
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
