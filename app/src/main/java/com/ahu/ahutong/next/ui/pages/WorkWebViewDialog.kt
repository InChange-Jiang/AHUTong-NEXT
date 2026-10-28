package com.ahu.ahutong.next.ui.pages

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.next.ui.theme.NextPalette

/**
 * 学习通作业题目只读查看。
 *
 * 只在用户点击时加载一次，不注入 JS 接口；命中风控或登录页立刻熔断，避免反复触发。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorkWebViewDialog(
    title: String,
    url: String,
    courseId: String,
    cookieHeader: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    val webView = remember {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieHeader.split("; ").forEach { entry ->
            if (entry.contains("=")) {
                cookieManager.setCookie("https://.chaoxing.com", entry)
            }
        }
        cookieManager.flush()

        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/126.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                private fun circuitBreak(view: WebView, currentUrl: String) {
                    when {
                        currentUrl.contains("antispiderShowVerify") -> {
                            view.stopLoading()
                            blocked = "触发超星风控验证，请过几小时再试"
                        }
                        currentUrl.contains("passport2.chaoxing.com/login") -> {
                            view.stopLoading()
                            blocked = "学习通登录态已失效，请到设置里重新登录学习通"
                        }
                    }
                }

                override fun onPageStarted(view: WebView, currentUrl: String, favicon: Bitmap?) {
                    loading = true
                    circuitBreak(view, currentUrl)
                }

                override fun onPageFinished(view: WebView, currentUrl: String) {
                    loading = false
                    circuitBreak(view, currentUrl)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        loading = false
                        pageError = true
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val host = request.url.host ?: return true
                    if (host == "chaoxing.com" || host.endsWith(".chaoxing.com")) return false
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    }
                    return true
                }
            }
            loadUrl(
                url,
                mapOf("Referer" to "https://mooc1.chaoxing.com/mooc2/work/list?courseId=$courseId"),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = NextPalette.Background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = NextPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NextPalette.Accent,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                when {
                    blocked != null -> Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            blocked.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NextPalette.Danger,
                        )
                        Text(
                            "这里只读展示题目，不会自动提交或重试。",
                            style = MaterialTheme.typography.labelSmall,
                            color = NextPalette.InkSecondary,
                        )
                    }

                    pageError -> Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "页面加载失败，请检查网络后重试。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NextPalette.Danger,
                        )
                    }

                    else -> AndroidView(
                        factory = { webView },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
