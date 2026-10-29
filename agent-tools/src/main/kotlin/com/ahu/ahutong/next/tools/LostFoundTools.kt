package com.ahu.ahutong.next.tools

import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.legacy.toChinese
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import com.ahu.ahutong.next.agent.ToolTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetLostFoundTool : Tool {
    override val spec = ToolSpec(
        name = "get_lost_found",
        displayName = "查询失物招领",
        description = "查询校园失物招领或寻物启事。state: 1=失物招领，2=寻物启事；可按校区、类型和关键词筛选。",
        parameters = schema(
            properties = buildJsonObject {
                put("state", intProp("1=失物招领，2=寻物启事；默认 1", minimum = 1, maximum = 2))
                put("campus", stringProp("校区名称，支持模糊匹配"))
                put("type", stringProp("物品类型，支持模糊匹配"))
                put("query", stringProp("标题关键词"))
                put("page", intProp("页码，默认 1", minimum = 1, maximum = 20))
                put("page_size", intProp("每页条数，默认 20", minimum = 1, maximum = 50))
            },
        ),
        timeoutMs = 30_000,
        kind = ToolKind.READ,
        source = "失物招领",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(false, error = ToolError("not_logged_in", "尚未登录安大通账号"))
        }
        val state = arguments.intOr("state", 1).coerceIn(1, 2)
        val page = arguments.intOr("page", 1).coerceIn(1, 20)
        val size = arguments.intOr("page_size", 20).coerceIn(1, 50)
        when (val result = AHURepository.getLostFoundList(page, size, state = state)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                ToolOutput(false, error = ToolError("lost_found_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> {
                val campus = arguments.stringOrNull("campus")
                val type = arguments.stringOrNull("type")
                val query = arguments.stringOrNull("query")
                val items = result.value.data.list.filter { item ->
                    (campus == null || item.campusName?.contains(campus, ignoreCase = true) == true) &&
                        (type == null || item.lostType?.typeName?.contains(type, ignoreCase = true) == true) &&
                        (query == null || item.title.contains(query, ignoreCase = true))
                }
                ToolOutput(
                    ok = true,
                    data = buildJsonObject {
                        put("state", if (state == 1) "失物招领" else "寻物启事")
                        put("total", result.value.data.total)
                        put("items", buildJsonArray {
                            items.forEach { item ->
                                add(buildJsonObject {
                                    put("id", item.id)
                                    put("title", item.title)
                                    put("type", item.lostType?.typeName)
                                    put("campus", item.campusName)
                                    put("time", item.createtime)
                                    put("linkman", item.linkman)
                                    put("phone", item.phone)
                                    put("mine", item.createuser == LegacyCampusApi.currentUserName())
                                })
                            }
                        })
                    },
                    trust = ToolTrust.UNTRUSTED,
                )
            }
        }
    }
}

class PublishLostFoundTool : Tool {
    override val spec = ToolSpec(
        name = "publish_lost_found",
        displayName = "发布失物招领",
        description = "发布一条失物招领或寻物启事。只有用户明确要求发布时才能调用；需要标题、描述、手机号、类型和校区。",
        parameters = schema(
            properties = buildJsonObject {
                put("state", intProp("1=失物招领，2=寻物启事", minimum = 1, maximum = 2))
                put("title", stringProp("标题"))
                put("description", stringProp("物品描述"))
                put("phone", stringProp("联系电话"))
                put("type", stringProp("物品类型名称或 ID"))
                put("campus", stringProp("校区名称或 ID"))
                put("linkman", stringProp("联系人，默认当前账号姓名"))
            },
            required = listOf("state", "title", "description", "phone", "type", "campus"),
        ),
        timeoutMs = 30_000,
        kind = ToolKind.WRITE,
        source = "失物招领",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        if (!LegacyCampusApi.isLoggedIn()) {
            return@withContext ToolOutput(false, error = ToolError("not_logged_in", "尚未登录安大通账号"))
        }
        val state = arguments.intOr("state", 1).coerceIn(1, 2)
        val title = arguments.stringOrNull("title")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少标题"))
        val description = arguments.stringOrNull("description")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少描述"))
        val phone = arguments.stringOrNull("phone")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少联系电话"))
        val typeKey = arguments.stringOrNull("type")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少类型"))
        val campusKey = arguments.stringOrNull("campus")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少校区"))
        val types = when (val result = AHURepository.getAllLostFoundType()) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                return@withContext ToolOutput(false, error = ToolError("lost_found_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> result.value.`object`
        }
        val campuses = when (val result = AHURepository.getAllCampus()) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                return@withContext ToolOutput(false, error = ToolError("lost_found_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> result.value.`object`
        }
        val type = types.firstOrNull { it.typeId == typeKey }
            ?: types.firstOrNull { it.typeName.contains(typeKey, ignoreCase = true) }
        val campus = campuses.firstOrNull { it.id == campusKey }
            ?: campuses.firstOrNull { it.campusName.contains(campusKey, ignoreCase = true) }
        if (type == null || campus == null) {
            return@withContext ToolOutput(
                false,
                error = ToolError(
                    "invalid_arguments",
                    "类型或校区无法匹配。类型：${types.joinToString { it.typeName }}；校区：${campuses.joinToString { it.campusName }}",
                ),
            )
        }
        val request = LostFoundPublishRequest(
            linkman = arguments.stringOrNull("linkman")
                ?: LegacyCampusApi.currentUserName().orEmpty(),
            phone = phone,
            typeid = type.typeId,
            num1 = description,
            campusid = campus.id,
            title = title,
            state = state.toString(),
        )
        when (val result = AHURepository.publishLostFound(request)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                ToolOutput(false, error = ToolError("lost_found_publish_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> ToolOutput(
                ok = true,
                data = buildJsonObject {
                    put("published", true)
                    put("state", if (state == 1) "失物招领" else "寻物启事")
                    put("type", type.typeName)
                    put("campus", campus.campusName)
                },
            )
        }
    }
}

class DeleteLostFoundTool : Tool {
    override val spec = ToolSpec(
        name = "delete_lost_found",
        displayName = "删除失物招领",
        description = "删除一条自己发布的失物招领/寻物启事。只有用户明确要求删除并提供 id 时才能调用。",
        parameters = schema(
            properties = buildJsonObject {
                put("id", stringProp("信息 ID，可从 get_lost_found 返回的 items[].id 获取"))
            },
            required = listOf("id"),
        ),
        timeoutMs = 30_000,
        kind = ToolKind.WRITE,
        source = "失物招领",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val id = arguments.stringOrNull("id")
            ?: return@withContext ToolOutput(false, error = ToolError("invalid_arguments", "缺少 id"))
        when (val result = AHURepository.deleteLostFound(id)) {
            is com.ahu.ahutong.core.common.AhuResult.Failure ->
                ToolOutput(false, error = ToolError("lost_found_delete_failed", result.error.toChinese()))
            is com.ahu.ahutong.core.common.AhuResult.Success -> ToolOutput(
                ok = true,
                data = buildJsonObject { put("deleted", true) },
            )
        }
    }
}
