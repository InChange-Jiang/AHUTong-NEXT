package com.ahu.ahutong.next.app.data

enum class AssistantPersonality(
    val id: String,
    val label: String,
    val description: String,
) {
    STANDARD(
        id = "standard",
        label = "标准",
        description = "清楚、稳、轻度关心。适合大多数查询和办事场景。",
    ),
    LIVELY(
        id = "lively",
        label = "活泼",
        description = "轻快、热情，偶尔口语和少量 emoji；不油腻、不刷屏。",
    ),
    CONCISE(
        id = "concise",
        label = "简洁",
        description = "只给结论和关键数据，不寒暄、不重复工具明细。",
    ),
    COMPANION(
        id = "companion",
        label = "陪伴",
        description = "更亲近、更软，有一点偏爱感，但保持分寸。",
    );

    companion object {
        fun fromId(value: String?): AssistantPersonality =
            entries.firstOrNull { it.id == value } ?: STANDARD
    }
}
