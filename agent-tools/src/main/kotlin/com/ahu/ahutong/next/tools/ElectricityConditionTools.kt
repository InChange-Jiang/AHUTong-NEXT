package com.ahu.ahutong.next.tools

import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.legacy.LegacyCampusApi
import com.ahu.ahutong.next.agent.Tool
import com.ahu.ahutong.next.agent.ToolError
import com.ahu.ahutong.next.agent.ToolKind
import com.ahu.ahutong.next.agent.ToolOutput
import com.ahu.ahutong.next.agent.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetElectricityConditionsTool : Tool {
    override val spec = ToolSpec(
        name = "get_electricity_conditions",
        displayName = "查询电控条件",
        description = "查询当前电费房间和最近使用过的电控条件（照明、空调等），用于切换前确认。",
        parameters = schema(properties = buildJsonObject { }),
        timeoutMs = 10_000,
        kind = ToolKind.READ,
        source = "电费系统",
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val current = currentSelection()
        val items = conditionItems()
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("current", current?.let { labelOf(it) })
                put("conditions", buildJsonArray {
                    items.forEachIndexed { index, item ->
                        add(buildJsonObject {
                            put("index", index)
                            put("label", item.label)
                            put("controller", item.selection.controller?.displayName ?: LegacyCampusApi.electricityController().displayName)
                            put("room", item.selection.room?.name)
                            put("updatedAt", item.updatedAt)
                        })
                    }
                })
            },
        )
    }
}

class SwitchElectricityConditionTool : Tool {
    override val spec = ToolSpec(
        name = "switch_electricity_condition",
        displayName = "切换电控条件",
        description = "把当前电费房间切换为最近使用过的某个条件（如照明/空调）。只改变后续查电费和充值使用的房间，不扣款。" +
            "index 来自 get_electricity_conditions；也可用 label 或 room 匹配。",
        parameters = schema(
            properties = buildJsonObject {
                put("index", intProp("条件序号，从 0 开始", minimum = 0))
                put("label", stringProp("条件名称，支持模糊匹配"))
                put("room", stringProp("房间名称，支持模糊匹配"))
            },
        ),
        timeoutMs = 15_000,
        kind = ToolKind.WRITE,
        source = "电费系统",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): ToolOutput {
        val items = conditionItems()
        if (items.isEmpty()) {
            return ToolOutput(false, error = ToolError("electricity_condition_empty", "还没有保存过电控条件"))
        }
        val index = arguments["index"]?.toString()?.toIntOrNull()
        val label = arguments.stringOrNull("label")
        val room = arguments.stringOrNull("room")
        val target = when {
            index != null -> items.getOrNull(index)
            label != null -> items.firstOrNull { it.label.contains(label, ignoreCase = true) }
            room != null -> items.firstOrNull {
                it.selection.room?.name?.contains(room, ignoreCase = true) == true
            }
            else -> null
        } ?: return ToolOutput(
            false,
            error = ToolError("electricity_condition_not_found", "没有匹配的电控条件，请先调用 get_electricity_conditions"),
        )
        val controller = target.selection.controller ?: LegacyCampusApi.electricityController()
        LegacyCampusApi.saveElectricitySelection(controller, target.selection)
        return ToolOutput(
            ok = true,
            data = buildJsonObject {
                put("label", target.label)
                put("controller", controller.displayName)
                put("room", target.selection.room?.name)
            },
        )
    }
}

private fun currentSelection(): RoomSelectionInfo? =
    LegacyCampusApi.savedElectricityRoom()?.takeIf { it.room != null }

private fun conditionItems(): List<ElectricityDepositHistoryItem> {
    val current = currentSelection()
    val history = LegacyCampusApi.electricityHistory()
    val all = if (current != null) {
        history + ElectricityDepositHistoryItem(
            selection = current,
            label = labelOf(current),
            updatedAt = 0L,
        )
    } else history
    return all.distinctBy { selectionKey(it.selection) }
}

private fun labelOf(selection: RoomSelectionInfo): String {
    val controller = selection.controller ?: LegacyCampusApi.electricityController()
    val place = selection.room?.name?.takeIf(String::isNotBlank)
        ?: listOfNotNull(selection.building?.name, selection.floor?.name)
            .joinToString(" · ")
            .takeIf(String::isNotBlank)
    return listOfNotNull(controller.displayName, place).joinToString(" · ")
}

private fun selectionKey(selection: RoomSelectionInfo): String = listOf(
    selection.controller?.name,
    selection.campus?.value,
    selection.building?.value,
    selection.floor?.value,
    selection.room?.value,
).joinToString("|") { it ?: "" }
