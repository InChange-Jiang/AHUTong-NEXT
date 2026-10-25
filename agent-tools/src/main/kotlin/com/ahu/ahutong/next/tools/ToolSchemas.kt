package com.ahu.ahutong.next.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun schema(properties: JsonObject, required: List<String> = emptyList()): JsonObject =
    buildJsonObject {
        put("type", "object")
        put("properties", properties)
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        put("additionalProperties", false)
    }

internal fun stringProp(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

internal fun intProp(description: String, minimum: Int? = null, maximum: Int? = null): JsonObject =
    buildJsonObject {
        put("type", "integer")
        put("description", description)
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

internal fun numberProp(description: String, minimum: Double? = null, maximum: Double? = null): JsonObject =
    buildJsonObject {
        put("type", "number")
        put("description", description)
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

internal fun JsonObject.intOr(key: String, default: Int): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: default

internal fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
