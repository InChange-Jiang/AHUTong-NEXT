package com.ahu.ahutong.next.ui.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private val inlineMath = Regex("""(?<!\$)\$(?!\$)[^$\n]+\$(?!\$)""")

fun String.containsMath(): Boolean =
    contains("$$") ||
        contains("\\[") ||
        contains("\\(") ||
        contains("\\begin{") ||
        inlineMath.containsMatchIn(this)

/**
 * KaTeX + markdown-it in a single local WebView. Only used for messages that actually
 * contain formulas; ordinary replies stay on the native Compose markdown renderer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KatexMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var height by remember { mutableStateOf(0) }
    var failed by remember(markdown) { mutableStateOf(false) }
    val html = remember(markdown) { buildMathHtml(context, markdown) }

    LaunchedEffect(markdown) {
        delay(2500)
        if (height == 0) failed = true
    }

    if (failed) {
        AppMarkdown(markdown = markdown, modifier = modifier)
        return
    }

    AndroidView(
        factory = { ctx ->
            val web = WebView(ctx)
            web.apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = false

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun post(h: Int) {
                            web.post { height = h }
                        }
                    },
                    "AndroidHeight",
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url ?: return true
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, url)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        return true
                    }
                }

                loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
                tag = html
            }
        },
        update = { web ->
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(if (height > 0) height.dp else 1.dp),
        onRelease = { it.destroy() },
    )
}

private fun buildMathHtml(context: Context, markdown: String): String {
    val base64 = Base64.encodeToString(
        markdown.toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP,
    )
    val template = context.assets.open("katex/math.html")
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
    return template.replace("__SOURCE_B64__", base64)
}
