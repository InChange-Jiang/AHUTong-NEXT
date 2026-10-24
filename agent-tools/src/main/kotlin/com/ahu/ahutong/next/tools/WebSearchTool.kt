package com.ahu.ahutong.next.tools

import com.ahu.ahutong.next.agent.AgentJson
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class SearchResult(
    val title: String = "",
    val url: String = "",
    val summary: String = "",
    val snippet: String = "",
    val date: String? = null,
    @SerialName("rerank_score") val rerankScore: Double? = null,
)

interface WebSearchClient {
    suspend fun search(query: String, count: Int): List<SearchResult>
}

/**
 * DeepSeek 官方 Search API（/v1/tools/search），与对话 API 共用同一把 API Key。
 */
class DeepSeekSearchClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : WebSearchClient {

    override suspend fun search(query: String, count: Int): List<SearchResult> =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "API Key 为空" }

            val payload = buildJsonObject {
                put("query", query)
                put("count", count.coerceIn(1, 50))
                put("need_content", false)
                put("ori_question", query)
            }

            val request = Request.Builder()
                .url(searchEndpoint(baseUrl))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("DeepSeek 搜索返回 ${response.code}：${body.take(300)}")
                }
                val parsed = runCatching {
                    AgentJson.decodeFromString(SearchResponse.serializer(), body)
                }.getOrElse {
                    throw IOException("DeepSeek 搜索响应无法解析：${body.take(300)}")
                }
                parsed.results
            }
        }

    private fun searchEndpoint(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return if (base.endsWith("/v1")) "$base/tools/search" else "$base/v1/tools/search"
    }
}

class WebSearchTool(private val client: WebSearchClient) : Tool {
    override val spec = ToolSpec(
        name = "web_search",
        displayName = "联网搜索",
        description = "使用 DeepSeek 搜索获取实时网页信息。适合新闻、政策、赛事、天气、校外资料等超出本地校园数据的问题。query 为搜索词，count 默认 5，最多 8。",
        parameters = schema(
            properties = buildJsonObject {
                put("query", stringProp("搜索词，1-128 字"))
                put("count", intProp("结果数量，默认 5", minimum = 1, maximum = 8))
            },
            required = listOf("query"),
        ),
        kind = ToolKind.READ,
        source = "DeepSeek 搜索",
        timeoutMs = 20_000,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val query = arguments.stringOrNull("query")
            ?: return ToolOutput(
                ok = false,
                error = ToolError("invalid_arguments", "query 不能为空"),
            )
        val count = arguments.intOr("count", 5).coerceIn(1, 8)
        val results = client.search(query, count)

        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("query", query)
                put("count", results.size)
                put("results", AgentJson.encodeToJsonElement(results))
            },
        )
    }
}

@Serializable
private data class SearchResponse(
    val results: List<SearchResult> = emptyList(),
)
