package com.ahu.ahutong.next.app.data

import android.content.Context
import android.util.Log

/** 从 assets/soul.md 取安筱桐的性格层；解析失败时回退为无性格附加。 */
class SoulRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cache = mutableMapOf<AssistantPersonality, String>()
    private val markdown by lazy {
        runCatching {
            appContext.assets.open("soul.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse {
            Log.w(TAG, "soul.md 读取失败", it)
            ""
        }
    }

    fun soulFor(personality: AssistantPersonality): String =
        cache.getOrPut(personality) { section(markdown, personality.label).orEmpty() }

    companion object {
        private const val TAG = "SoulRepository"

        internal fun section(markdown: String, label: String): String? {
            val lines = markdown.lines()
            val start = lines.indexOfFirst { it.trim() == "## $label" }
            if (start < 0) return null
            return lines.drop(start + 1)
                .takeWhile { !it.trimStart().startsWith("## ") }
                .joinToString("\n")
                .trim()
                .ifBlank { null }
        }
    }
}
