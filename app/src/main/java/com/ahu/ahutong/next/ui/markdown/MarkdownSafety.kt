package com.ahu.ahutong.next.ui.markdown

import androidx.compose.ui.platform.UriHandler

/**
 * 助手回复是不可信内容：渲染前先去掉图片、过滤掉非 http(s) 链接，避免
 * 恶意 Markdown 触发图片信标或把数据塞进自定义 scheme 的跳转里。
 *
 * 这里不做完整 Markdown 解析，只处理最容易造成外带的两种节点；围栏代码块原样保留。
 */
object MarkdownSafety {
    private val image = Regex("""!\[([^\]]*)]\(((?:[^()]|\([^()]*\))*)\)""")
    private val link = Regex("""\[([^\]]+)]\(((?:[^()]|\([^()]*\))*)\)""")
    private val rawImage = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val autoLink = Regex("""<([a-zA-Z][a-zA-Z0-9+.-]*:[^>\s]*)>""")

    fun sanitize(markdown: String): String {
        val out = StringBuilder(markdown.length)
        var inFence = false
        markdown.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                out.appendLine(line)
                return@forEach
            }
            if (inFence) {
                out.appendLine(line)
                return@forEach
            }

            var safe = image.replace(line) { match ->
                val alt = match.groupValues[1].ifBlank { "已省略" }
                "[图片：$alt]"
            }
            safe = rawImage.replace(safe, "[图片已省略]")
            safe = link.replace(safe) { match ->
                val text = match.groupValues[1]
                val url = match.groupValues[2].trim()
                if (isSafeExternalUri(url)) match.value else text
            }
            safe = autoLink.replace(safe) { match ->
                val url = match.groupValues[1]
                if (isSafeExternalUri(url)) match.value else url
            }
            out.appendLine(safe)
        }
        return out.toString().trimEnd()
    }

    fun isSafeExternalUri(uri: String): Boolean {
        val value = uri.trim().lowercase()
        return value.startsWith("https://") || value.startsWith("http://")
    }
}

/**
 * 渲染层最后一道闸：即使 Markdown 解析器把某个链接当成可点击节点，
 * 也只放行 http(s)，其余 scheme 不交给系统打开。
 */
class SafeUriHandler(
    private val delegate: UriHandler,
    private val onBlocked: () -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (MarkdownSafety.isSafeExternalUri(uri)) {
            delegate.openUri(uri)
        } else {
            onBlocked()
        }
    }
}
