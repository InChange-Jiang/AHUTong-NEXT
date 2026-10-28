package com.ahu.ahutong.next.app.data

import com.ahu.ahutong.next.data.Assignment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** 学习通作业本地缓存；进入页面只读这里，主动刷新或 AI 查询才回源。 */
class AssignmentCacheStore(context: android.content.Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(accountId: String): File =
        File(appContext.filesDir, "assignments_cache_${accountId.ifBlank { "anon" }}.json")

    fun load(accountId: String): List<Assignment> = runCatching {
        val target = file(accountId)
        if (!target.exists()) emptyList() else json.decodeFromString(
            ListSerializer(Assignment.serializer()),
            target.readText(),
        )
    }.getOrDefault(emptyList())

    fun save(accountId: String, items: List<Assignment>) {
        runCatching {
            file(accountId).writeText(json.encodeToString(ListSerializer(Assignment.serializer()), items))
        }
    }

    /** 缓存文件最后写入时间，用于页头“上次同步”。 */
    fun lastModified(accountId: String): Long =
        runCatching { file(accountId).lastModified() }.getOrDefault(0L)
}
