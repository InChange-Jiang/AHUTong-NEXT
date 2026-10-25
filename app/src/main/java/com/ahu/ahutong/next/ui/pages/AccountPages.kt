package com.ahu.ahutong.next.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.crawler.model.jwxt.StudentInfo
import com.ahu.ahutong.data.session.SavedAccount
import com.ahu.ahutong.data.session.SavedAccounts
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.BuildConfig
import com.ahu.ahutong.next.R
import com.ahu.ahutong.next.app.data.SettingsRepository
import com.ahu.ahutong.next.ui.components.AgentAvatar
import com.ahu.ahutong.next.ui.components.AsyncContent
import com.ahu.ahutong.next.ui.components.FeatureScaffold
import com.ahu.ahutong.next.ui.components.FeatureScrollColumn
import com.ahu.ahutong.next.ui.components.LoadState
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.toLoadFailure
import com.ahu.ahutong.next.ui.settings.AccountLoginDialog
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfilePage(
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    var state by remember { mutableStateOf<LoadState<StudentInfo>>(LoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        state = LoadState.Loading
        state = runCatching {
            withContext(Dispatchers.IO) { LegacyCampusApi.studentInfo().requireValue() }
        }.fold(onSuccess = { LoadState.Ready(it) }, onFailure = { it.toLoadFailure() })
    }
    FeatureScaffold("个人资料", onBack) {
        AsyncContent(state, onRetry = { reload++ }) {
            val info = (state as LoadState.Ready).value
            FeatureScrollColumn {
                PageCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AgentAvatar(size = 54.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                info.name.ifBlank { "未登录" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NextPalette.Ink,
                            )
                            Text(
                                LegacyCampusApi.currentStudentId().orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = NextPalette.InkSecondary,
                            )
                        }
                    }
                }
                PageCard {
                    LabelValueRow("学院", info.department.orEmpty().ifBlank { "—" })
                    LabelValueRow("专业", info.major.orEmpty().ifBlank { "—" })
                    LabelValueRow("班级", info.adminClass.orEmpty().ifBlank { "—" })
                    LabelValueRow("层次", info.educationLevel.orEmpty().ifBlank { "—" })
                    LabelValueRow("校区", info.campus.orEmpty().ifBlank { "—" })
                    LabelValueRow("手机", info.phoneMasked.orEmpty().ifBlank { "—" })
                    LabelValueRow("邮箱", info.email.orEmpty().ifBlank { "—" })
                }
                PageCard {
                    SoftSecondaryButton("账号与切换", onClick = onOpenAccounts, modifier = Modifier.fillMaxWidth())
                    SoftSecondaryButton(
                        "退出登录",
                        onClick = {
                            LegacyCampusApi.logout()
                            onLoggedOut()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun AccountsPage(
    onBack: () -> Unit,
    onAccountChanged: () -> Unit,
) {
    var accounts by remember { mutableStateOf<List<SavedAccount>>(emptyList()) }
    var showLogin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmForget by remember { mutableStateOf<SavedAccount?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentId = LegacyCampusApi.currentStudentId()

    LaunchedEffect(reload) {
        LegacyCampusApi.rememberCurrentAccount()
        accounts = runCatching { SavedAccounts.accounts() }.getOrDefault(emptyList())
    }

    FeatureScaffold(
        title = "账号与切换",
        onBack = onBack,
        actions = { TextButton(onClick = { showLogin = true }) { Text("添加") } },
    ) {
        FeatureScrollColumn {
            PageCard {
                Text("当前账号", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                Text(
                    LegacyCampusApi.currentUserName().orEmpty().ifBlank { "未登录" },
                    style = MaterialTheme.typography.titleMedium,
                    color = NextPalette.Ink,
                )
                Text(currentId.orEmpty(), style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
            }
            if (accounts.isEmpty()) {
                PageCard {
                    Text(
                        "还没有保存的账号。登录一次后会记住一个账号，下次可以一键切换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                }
            } else {
                PageCard {
                    accounts.forEachIndexed { index, account ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.name, style = MaterialTheme.typography.bodyMedium, color = NextPalette.Ink)
                                Text(account.userId, style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                            }
                            if (account.userId == currentId) {
                                Text("当前", style = MaterialTheme.typography.labelSmall, color = NextPalette.Success)
                            } else {
                                TextButton(
                                    enabled = busy == null,
                                    onClick = {
                                        busy = account.userId
                                        error = null
                                        scope.launch {
                                            val password = runCatching { SavedAccounts.password(account.userId) }.getOrNull()
                                            if (password.isNullOrBlank()) {
                                                error = "这个账号没有保存密码，请重新登录添加。"
                                                busy = null
                                                return@launch
                                            }
                                            val result = withContext(Dispatchers.IO) {
                                                LegacyCampusApi.login(account.userId, password)
                                            }
                                            when (result) {
                                                is LegacyCampusApi.LoginResult.Success -> onAccountChanged()
                                                is LegacyCampusApi.LoginResult.WebVerificationRequired -> {
                                                    error = "该账号需要网页安全验证，请在设置里重新登录。"
                                                }
                                                is LegacyCampusApi.LoginResult.Failure -> error = result.message
                                            }
                                            busy = null
                                        }
                                    },
                                ) { Text(if (busy == account.userId) "切换中…" else "切换") }
                            }
                            TextButton(onClick = { confirmForget = account }) { Text("移除") }
                        }
                        if (index != accounts.lastIndex) HorizontalDivider(color = NextPalette.Line)
                    }
                }
            }
        }
    }

    if (showLogin) {
        AccountLoginDialog(
            onDismiss = { showLogin = false },
            onSuccess = {
                showLogin = false
                reload++
                onAccountChanged()
            },
        )
    }

    confirmForget?.let { account ->
        AlertDialog(
            onDismissRequest = { confirmForget = null },
            title = { Text("移除保存的账号？") },
            text = { Text("${account.name}（${account.userId}）的本地保存密码会被删除，不影响云端数据。") },
            confirmButton = {
                TextButton(onClick = {
                    SavedAccounts.forget(account.userId)
                    confirmForget = null
                    reload++
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = null }) { Text("取消") } },
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("切换失败") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } },
        )
    }
}

@Composable
fun AboutPage(onBack: () -> Unit) {
    FeatureScaffold("关于", onBack) {
        FeatureScrollColumn {
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("安大通NEXT", style = MaterialTheme.typography.titleMedium, color = NextPalette.Ink)
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }
                }
                Text(
                    "对话是入口，页面是兜底。安筱桐负责把多步操作压成一句话，页面保证没有模型时也能完成同样的事。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
            }
            PageCard {
                Text("数据与隐私", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                Text(
                    "教务、校园卡与生活服务数据直接来自学校系统，不经过安大通服务器。API Key 与密码只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
            }
            PageCard {
                Text("开源组件", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                Text(
                    "Markdown 渲染基于 MarkdownReader（GPL-3.0）；图标使用 IconPark（Apache-2.0）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
            }
        }
    }
}

@Composable
fun AppearancePage(
    displayScale: Float,
    onDisplayScaleChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    FeatureScaffold("外观与显示", onBack) {
        FeatureScrollColumn {
            PageCard {
                Text("显示大小", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                Text(
                    "调整聊天、卡片与所有页面的整体大小。小屏建议 88%–95%，大屏可适当放大。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
                androidx.compose.material3.Slider(
                    value = displayScale,
                    onValueChange = { onDisplayScaleChange((it * 100).toInt() / 100f) },
                    valueRange = SettingsRepository.MIN_DISPLAY_SCALE..SettingsRepository.MAX_DISPLAY_SCALE,
                )
                Text(
                    "当前 ${(displayScale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = NextPalette.AccentDeep,
                )
                SoftSecondaryButton("恢复标准", onClick = { onDisplayScaleChange(1f) })
            }
            PageCard {
                Text("主题", style = MaterialTheme.typography.titleSmall, color = NextPalette.Ink)
                Text(
                    "当前版本使用浅色轻拟物主题。深色模式与自定义背景会在 UI 打磨阶段提供。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
            }
        }
    }
}
