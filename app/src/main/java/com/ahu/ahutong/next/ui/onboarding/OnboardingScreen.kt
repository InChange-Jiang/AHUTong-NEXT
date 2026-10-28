package com.ahu.ahutong.next.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.app.data.SettingsRepository
import com.ahu.ahutong.next.app.data.modelPresets
import com.ahu.ahutong.next.ui.components.AgentAvatar
import com.ahu.ahutong.next.ui.components.SoftBackground
import com.ahu.ahutong.next.ui.components.SoftChip
import com.ahu.ahutong.next.ui.components.SoftChipStrip
import com.ahu.ahutong.next.ui.components.SoftIconButton
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.SoftTextField
import com.ahu.ahutong.next.ui.pages.PageCard
import com.ahu.ahutong.next.ui.settings.AccountLoginDialog
import com.ahu.ahutong.next.ui.markdown.AppMarkdown
import com.ahu.ahutong.next.ui.settings.ChaoxingLoginDialog
import com.ahu.ahutong.next.ui.theme.NextPalette
import com.ahu.ahutong.next.R
import kotlinx.coroutines.delay

private const val STEP_COUNT = 5

private enum class LegalDoc(val title: String, val asset: String) {
    TERMS("用户协议", "legal/terms.md"),
    PRIVACY("隐私政策", "legal/privacy.md"),
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var step by remember { mutableIntStateOf(0) }
    var legalDoc by remember { mutableStateOf<LegalDoc?>(null) }

    var loggedIn by remember { mutableStateOf(LegacyCampusApi.isLoggedIn()) }
    var userName by remember { mutableStateOf(LegacyCampusApi.currentUserName().orEmpty()) }
    var chaoxingConnected by remember { mutableStateOf(LegacyCampusApi.hasChaoxingSession()) }
    var showAccountLogin by remember { mutableStateOf(false) }
    var showChaoxingLogin by remember { mutableStateOf(false) }

    var baseUrl by remember { mutableStateOf(settings.baseUrl()) }
    var model by remember { mutableStateOf(settings.model()) }
    var apiKey by remember { mutableStateOf("") }
    var hasSavedKey by remember { mutableStateOf(!settings.apiKey().isNullOrBlank()) }

    LaunchedEffect(step) {
        if (step == 1) {
            loggedIn = LegacyCampusApi.isLoggedIn()
            userName = LegacyCampusApi.currentUserName().orEmpty()
            chaoxingConnected = LegacyCampusApi.hasChaoxingSession()
        }
    }

    BackHandler(enabled = legalDoc != null) { legalDoc = null }

    SoftBackground {
        val openDoc = legalDoc
        if (openDoc != null) {
            LegalReadingPage(doc = openDoc, onBack = { legalDoc = null })
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (step > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(STEP_COUNT - 1) { index ->
                            val active = index == step - 1
                            Box(
                                modifier = Modifier
                                    .size(if (active) 18.dp else 7.dp, 7.dp)
                                    .background(
                                        if (active) NextPalette.Accent else NextPalette.Line,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 5 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboardingStep",
            ) { activeStep ->
                if (activeStep == 0) {
                    WelcomeStep()
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (activeStep) {
                            1 -> LoginStep(
                                loggedIn = loggedIn,
                                userName = userName,
                                chaoxingConnected = chaoxingConnected,
                                onLogin = { showAccountLogin = true },
                                onChaoxing = { showChaoxingLogin = true },
                            )

                            2 -> ApiStep(
                                baseUrl = baseUrl,
                                model = model,
                                apiKey = apiKey,
                                hasSavedKey = hasSavedKey,
                                onBaseUrl = { baseUrl = it },
                                onModel = { model = it },
                                onApiKey = { apiKey = it },
                            )

                            3 -> LegalStep(
                                onOpenTerms = { legalDoc = LegalDoc.TERMS },
                                onOpenPrivacy = { legalDoc = LegalDoc.PRIVACY },
                            )

                            else -> TourStep()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    SoftSecondaryButton(
                        text = "上一步",
                        onClick = { step-- },
                        modifier = Modifier.weight(1f),
                    )
                }
                when (step) {
                    0 -> SoftPrimaryButton(
                        text = "开始 →",
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    1 -> SoftPrimaryButton(
                        text = "下一步",
                        onClick = { step = 2 },
                        enabled = loggedIn,
                        modifier = Modifier.weight(1.4f),
                    )

                    2 -> SoftPrimaryButton(
                        text = "保存并继续",
                        onClick = {
                            settings.save(
                                baseUrl = baseUrl,
                                model = model,
                                apiKey = apiKey.takeIf { it.isNotBlank() },
                            )
                            hasSavedKey = true
                            step = 3
                        },
                        enabled = (apiKey.isNotBlank() || hasSavedKey) &&
                            baseUrl.isNotBlank() && model.isNotBlank(),
                        modifier = Modifier.weight(1.4f),
                    )

                    3 -> SoftPrimaryButton(
                        text = "同意并继续",
                        onClick = { step = 4 },
                        modifier = Modifier.weight(1.4f),
                    )

                    else -> SoftPrimaryButton(
                        text = "开始使用",
                        onClick = onComplete,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
        }
    }

    if (showAccountLogin) {
        AccountLoginDialog(
            onDismiss = { showAccountLogin = false },
            onSuccess = { name ->
                loggedIn = true
                userName = name
                showAccountLogin = false
            },
        )
    }
    if (showChaoxingLogin) {
        ChaoxingLoginDialog(
            onDismiss = { showChaoxingLogin = false },
            onSuccess = {
                chaoxingConnected = true
                showChaoxingLogin = false
            },
        )
    }
}

@Composable
private fun LoginStep(
    loggedIn: Boolean,
    userName: String,
    chaoxingConnected: Boolean,
    onLogin: () -> Unit,
    onChaoxing: () -> Unit,
) {
    StepTitle("先登录智慧安大", "课表、成绩、考试、校园卡等数据都来自这里。")
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (loggedIn) userName.ifBlank { "已登录" } else "智慧安大账号",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NextPalette.Ink,
                )
                Text(
                    if (loggedIn) "已登录，可以继续" else "必须登录后才能使用校园数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (loggedIn) NextPalette.Success else NextPalette.InkSecondary,
                )
            }
            if (!loggedIn) {
                SoftSecondaryButton(text = "登录", onClick = onLogin)
            }
        }
    }
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "学习通（可选）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NextPalette.Ink,
                )
                Text(
                    if (chaoxingConnected) "已连接，作业使用真实数据" else "不连接也能用，只是没有作业数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chaoxingConnected) NextPalette.Success else NextPalette.InkSecondary,
                )
            }
            if (!chaoxingConnected) {
                SoftSecondaryButton(text = "连接", onClick = onChaoxing)
            }
        }
    }
    Text(
        "登录信息只保存在本机，不会上传到安大通服务器。",
        style = MaterialTheme.typography.labelSmall,
        color = NextPalette.InkSecondary,
    )
}

@Composable
private fun ApiStep(
    baseUrl: String,
    model: String,
    apiKey: String,
    hasSavedKey: Boolean,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onApiKey: (String) -> Unit,
) {
    StepTitle("配置模型 API Key", "安筱桐需要你自己的模型 Key 才能对话；Key 只存本机 Keystore。")
    PageCard {
        Text(
            "厂商预设",
            style = MaterialTheme.typography.labelMedium,
            color = NextPalette.InkSecondary,
        )
        SoftChipStrip {
            modelPresets.forEach { preset ->
                SoftChip(
                    preset.label,
                    onClick = {
                        onBaseUrl(preset.baseUrl)
                        onModel(preset.model)
                    },
                    selected = baseUrl == preset.baseUrl && model == preset.model,
                )
            }
        }
        SoftTextField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            label = "Base URL",
            placeholder = "https://api.deepseek.com/v1",
            modifier = Modifier.fillMaxWidth(),
        )
        SoftTextField(
            value = model,
            onValueChange = onModel,
            label = "模型名",
            placeholder = "deepseek-flash",
            modifier = Modifier.fillMaxWidth(),
        )
        SoftTextField(
            value = apiKey,
            onValueChange = onApiKey,
            label = "API Key",
            placeholder = if (hasSavedKey) "已保存，留空则不修改" else "粘贴你的 API Key",
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "可以稍后在「设置 → 模型与性格」里修改。",
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
        )
    }
}

@Composable
private fun TourStep() {
    StepTitle("你会用到这些", "三个一级页面，从左往右滑，或点顶部圆点切换。")
    TourCard("对话", "直接问课表、成绩、作业；输入框左侧是校园卡付款码，抽屉里有历史对话。")
    TourCard("课表", "按整学期展示，周数字选中后居中；设置里可切换学期、显示周末和自定义课程。")
    TourCard("日程", "作业和自建日程铺在月历上；日视图可切到「仅看日程」，只看自己添加的安排，月历仍保留作业。")
}

@Composable
private fun WelcomeStep() {
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        stage = 1
        delay(280)
        stage = 2
        delay(320)
        stage = 3
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = stage >= 1,
            enter = fadeIn(tween(700)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(700)),
        ) {
            AgentAvatar(size = 116.dp)
        }
        Spacer(Modifier.height(30.dp))
        AnimatedVisibility(
            visible = stage >= 2,
            enter = fadeIn(tween(700)) + slideInVertically { it / 8 },
        ) {
            Text(
                "欢迎使用安大通NEXT！",
                style = MaterialTheme.typography.displaySmall,
                color = NextPalette.Ink,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(
            visible = stage >= 3,
            enter = fadeIn(tween(700)) + slideInVertically { it / 8 },
        ) {
            Text(
                "为了快速上手，请允许我们为您做简单的指引。",
                style = MaterialTheme.typography.bodyMedium,
                color = NextPalette.InkSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LegalStep(
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    StepTitle("用户协议与隐私政策", "开始之前，用一分钟说清楚我们怎么处理你的数据。")
    LegalSummaryCard(
        title = "隐私政策",
        body = "账号、课表、成绩、校园卡等数据直接来自学校系统；API Key 与聊天记录只保存在本机；对话会发送给你选择的模型服务商；必吃榜只上传筛选条件，不含身份信息。",
        onClick = onOpenPrivacy,
    )
    LegalSummaryCard(
        title = "用户协议",
        body = "安大通NEXT 是学生团队开发的非官方工具，AI 回复可能出错；涉及缴费等操作都会先请你本人确认，支付密码不会离开本机。",
        onClick = onOpenTerms,
    )
    Text(
        "继续即表示你已阅读并同意《用户协议》与《隐私政策》。",
        style = MaterialTheme.typography.labelSmall,
        color = NextPalette.InkSecondary,
    )
}

@Composable
private fun LegalSummaryCard(
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    PageCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NextPalette.Ink,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "查看全文",
                style = MaterialTheme.typography.labelMedium,
                color = NextPalette.AccentDeep,
            )
        }
    }
}

@Composable
private fun LegalReadingPage(doc: LegalDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    val content = remember(doc) {
        runCatching {
            context.assets.open(doc.asset).bufferedReader().use { it.readText() }
        }.getOrElse { "文档加载失败：${it.message ?: "未知错误"}" }
    }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
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
                doc.title,
                style = MaterialTheme.typography.titleLarge,
                color = NextPalette.Ink,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            AppMarkdown(content)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NextPalette.Ink,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = NextPalette.InkSecondary,
        )
    }
}

@Composable
private fun TourCard(title: String, body: String) {
    PageCard {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = NextPalette.Ink,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = NextPalette.InkSecondary,
        )
    }
}
