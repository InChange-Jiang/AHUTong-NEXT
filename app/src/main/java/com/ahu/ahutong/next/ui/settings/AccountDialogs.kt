package com.ahu.ahutong.next.ui.settings

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.ui.components.SoftPrimaryButton
import com.ahu.ahutong.next.ui.components.SoftSecondaryButton
import com.ahu.ahutong.next.ui.components.SoftSurface
import com.ahu.ahutong.next.ui.components.SoftTextField
import com.ahu.ahutong.next.ui.theme.NextPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val JWXT_HOST = "jw.ahu.edu.cn"
private const val CAS_HOST = "one.ahu.edu.cn"
private const val JWXT_HOME_PATH = "/student/home"
private const val JWXT_LOGIN_PATH = "/student/sso/login"
private const val CAS_LOGIN_PATH = "/cas/login"

private val COOKIE_URLS = listOf(
    "https://jw.ahu.edu.cn/",
    "https://jw.ahu.edu.cn/student/",
    "https://jw.ahu.edu.cn/student/home",
    "https://one.ahu.edu.cn/",
    "https://one.ahu.edu.cn/cas/",
    "https://one.ahu.edu.cn/cas/login",
)

@Composable
fun AccountLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit,
) {
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var webVerification by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (studentId.isBlank() || password.isBlank() || loading) return
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LegacyCampusApi.login(studentId.trim(), password)
            }
            loading = false
            when (result) {
                is LegacyCampusApi.LoginResult.Success -> onSuccess(result.name)
                is LegacyCampusApi.LoginResult.WebVerificationRequired -> webVerification = true
                is LegacyCampusApi.LoginResult.Failure -> error = result.message
            }
        }
    }

    if (webVerification) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SoftSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                shape = RoundedCornerShape(28.dp),
                elevation = 18.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "教务安全验证",
                        style = MaterialTheme.typography.titleMedium,
                        color = NextPalette.Ink,
                    )
                    Text(
                        "智慧安大已通过。学校要求再确认一次教务登录，页面会自动填写；若出现滑块或短信验证，请手动完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextPalette.InkSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    JwxtVerificationWebView(
                        studentId = studentId.trim(),
                        password = password,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp),
                        onVerified = { cookies ->
                            loading = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    LegacyCampusApi.completeWebVerification(cookies)
                                }
                                loading = false
                                when (result) {
                                    is LegacyCampusApi.LoginResult.Success -> onSuccess(result.name)
                                    is LegacyCampusApi.LoginResult.Failure -> {
                                        webVerification = false
                                        error = result.message
                                    }
                                    else -> {
                                        webVerification = false
                                        error = "验证未完成，请重试"
                                    }
                                }
                            }
                        },
                        onFailed = {
                            webVerification = false
                            error = it
                        },
                    )
                    if (loading) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp,
                                color = NextPalette.Accent,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "正在导入教务会话…",
                                style = MaterialTheme.typography.labelSmall,
                                color = NextPalette.InkSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SoftSecondaryButton(text = "取消", onClick = onDismiss)
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        SoftSurface(shape = RoundedCornerShape(28.dp), elevation = 18.dp) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("登录安大通账号", style = MaterialTheme.typography.titleMedium, color = NextPalette.Ink)
                Text(
                    "使用本科教务账号。密码只用于向学校系统换取会话，NEXT 不会把它上传到自己的服务器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
                SoftTextField(
                    value = studentId,
                    onValueChange = { studentId = it },
                    label = "学号",
                    modifier = Modifier.fillMaxWidth(),
                )
                SoftTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = NextPalette.Danger)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftSecondaryButton(text = "取消", onClick = onDismiss)
                    if (loading) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp,
                                color = NextPalette.Accent,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("登录中…", style = MaterialTheme.typography.labelSmall, color = NextPalette.InkSecondary)
                        }
                    } else {
                        SoftPrimaryButton(text = "登录", onClick = ::submit)
                    }
                }
            }
        }
    }
}

@Composable
fun ChaoxingLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        SoftSurface(shape = RoundedCornerShape(28.dp), elevation = 18.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("登录学习通", style = MaterialTheme.typography.titleMedium, color = NextPalette.Ink)
                Text(
                    "作业数据来自学习通，和教务账号是两套登录。账号与密码只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NextPalette.InkSecondary,
                )
                SoftTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = "学习通账号",
                    modifier = Modifier.fillMaxWidth(),
                )
                SoftTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = NextPalette.Danger)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftSecondaryButton(text = "取消", onClick = onDismiss)
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp,
                            color = NextPalette.Accent,
                        )
                    } else {
                        SoftPrimaryButton(
                            text = "登录",
                            onClick = {
                                if (account.isBlank() || password.isBlank()) return@SoftPrimaryButton
                                loading = true
                                error = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        LegacyCampusApi.loginChaoxing(account.trim(), password)
                                    }
                                    loading = false
                                    when (result) {
                                        is com.ahu.ahutong.core.common.AhuResult.Success -> onSuccess()
                                        is com.ahu.ahutong.core.common.AhuResult.Failure -> {
                                            error = result.error.toChinese()
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun JwxtVerificationWebView(
    studentId: String,
    password: String,
    modifier: Modifier = Modifier,
    onVerified: (String) -> Unit,
    onFailed: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.userAgentString = com.ahu.ahutong.data.AHURepository.jwxtBrowserUserAgent
                webChromeClient = WebChromeClient()
                val current = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAcceptThirdPartyCookies(current, true)
                    }
                }
                webViewClient = object : WebViewClient() {
                    private var completed = false
                    private var submitted = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (completed || view == null || url.isNullOrBlank()) return
                        val uri = Uri.parse(url)
                        when {
                            uri.host.equals(JWXT_HOST, true) &&
                                uri.path?.trimEnd('/') == JWXT_HOME_PATH -> {
                                completed = true
                                CookieManager.getInstance().flush()
                                onVerified(captureCookies())
                            }

                            uri.host.equals(CAS_HOST, true) &&
                                uri.path?.trimEnd('/') == CAS_LOGIN_PATH -> {
                                if (!submitted) {
                                    submitted = true
                                    view.fillCas(studentId, password)
                                }
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val host = request?.url?.host.orEmpty()
                        return !(host == "ahu.edu.cn" || host.endsWith(".ahu.edu.cn"))
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request?.isForMainFrame == true && errorResponse?.statusCode != 412) {
                            onFailed("教务页面返回 ${errorResponse?.statusCode}")
                        }
                        super.onReceivedHttpError(view, request, errorResponse)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            onFailed(error?.description?.toString() ?: "教务页面加载失败")
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        handler?.cancel()
                        onFailed("教务页面证书校验失败")
                    }
                }
                loadUrl(LegacyCampusApi.WEB_LOGIN_URL)
                webView = this
            }
        },
    )
}

private fun WebView.fillCas(studentId: String, password: String? = null) {
    val script = """
        (function() {
            var u = document.getElementById('un');
            var p = document.getElementById('pd');
            var b = document.getElementById('index_login_btn');
            if (!u || !p || !b) return 'missing';
            u.value = ${JSONObject.quote(studentId)};
            p.value = ${JSONObject.quote(password.orEmpty())};
            u.dispatchEvent(new Event('input', { bubbles: true }));
            p.dispatchEvent(new Event('input', { bubbles: true }));
            b.click();
            return 'submitted';
        })();
    """.trimIndent()
    evaluateJavascript(script, null)
}

private fun captureCookies(): String {
    val manager = CookieManager.getInstance()
    manager.flush()
    val array = JSONArray()
    val seen = HashSet<String>()
    COOKIE_URLS.forEach { url ->
        val domain = Uri.parse(url).host ?: return@forEach
        manager.getCookie(url).orEmpty().split(';').forEach { raw ->
            val item = raw.trim()
            val index = item.indexOf('=')
            if (index <= 0) return@forEach
            val name = item.substring(0, index)
            val value = item.substring(index + 1)
            if (!seen.add("$domain|$name")) return@forEach
            array.put(
                JSONObject().apply {
                    put("name", name)
                    put("value", value)
                    put("domain", domain)
                    put("path", "/")
                    put("secure", true)
                    put("http_only", false)
                },
            )
        }
    }
    return array.toString()
}
