package com.ahu.ahutong.next.tools

import com.ahu.ahutong.data.canteen.CanteenApi
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetCanteenRankingTool : Tool {
    override val spec = ToolSpec(
        name = "get_canteen_ranking",
        displayName = "查询必吃榜",
        description = "查询校园食堂窗口热度榜。period: week/month/all；campus: all/qingyuan/longhe/jinzailu。返回冠军窗口与排名。",
        parameters = schema(
            properties = buildJsonObject {
                put("period", stringProp("统计周期：week=本周，month=本月，all=全部；默认 week"))
                put("campus", stringProp("校区：all/qingyuan/longhe/jinzailu，默认 all"))
                put("limit", intProp("返回条目数，默认 20", minimum = 1, maximum = 50))
            },
        ),
        timeoutMs = 30_000,
        kind = ToolKind.READ,
        source = "必吃榜",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val period = when (arguments.stringOrNull("period")) {
            "month", "本月" -> "month"
            "all", "全部" -> "all"
            else -> "week"
        }
        val campus = when (arguments.stringOrNull("campus")) {
            "qingyuan", "磬苑" -> "磬苑"
            "longhe", "龙河" -> "龙河"
            "jinzailu", "金寨路" -> "金寨路"
            else -> null
        }
        val canteens = when (campus) {
            "磬苑" -> "榴园,桔园,梅园,桂园,蕙园食堂,梧桐园食堂"
            "龙河" -> "本部二食堂,本部三食堂,二食堂餐厅,三食堂,外招餐厅"
            "金寨路" -> "江淮学院食堂"
            else -> null
        }
        val limit = arguments.intOr("limit", 20).coerceIn(1, 50)
        runCatching {
            CanteenApi.API.insights(period = period, limit = limit, canteens = canteens, minTxns = 20)
        }.fold(
            onSuccess = { data ->
                ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("period", data.period ?: period)
                        put("campus", campus ?: "全部")
                        put("sampleTxns", data.sample?.txns ?: 0)
                        data.superlatives?.topWindow?.let { top ->
                            put("topWindow", buildJsonObject {
                                put("name", top.name ?: "未命名窗口")
                                put("canteen", top.canteen)
                                put("floor", top.floor)
                                put("txns", top.txns)
                            })
                        }
                        put("ranking", buildJsonArray {
                            data.ranking.orEmpty().take(limit).forEach { item ->
                                add(buildJsonObject {
                                    put("rank", item.rank)
                                    put("name", item.name ?: "未命名窗口")
                                    put("canteen", item.canteen)
                                    put("floor", item.floor)
                                    put("txns", item.txns)
                                    put("avgYuan", item.avgCentsPerTxn?.div(100.0))
                                })
                            }
                        })
                    },
                )
            },
            onFailure = { error ->
                ToolOutput(false, error = ToolError("canteen_failed", error.message ?: "必吃榜查询失败"))
            },
        )
    }
}
