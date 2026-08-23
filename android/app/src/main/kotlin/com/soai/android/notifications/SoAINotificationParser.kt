// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import org.json.JSONArray
import org.json.JSONObject

object SoAINotificationParser {

    fun parseCreatedEvent(payload: JSONObject): SoAINotificationRecord? {
        val id = payload.optionalString("notification_id") ?: return null
        val type = parseType(payload.optionalString("notification_type")) ?: return null
        return parseRecordBody(payload, id, type)
    }

    fun parseListResponse(body: String): List<SoAINotificationRecord> {
        val root = JSONObject(body)
        val array = root.optJSONArray("notifications") ?: JSONArray()
        val records = mutableListOf<SoAINotificationRecord>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseRecord(item)?.let { records.add(it) }
        }
        return records
    }

    fun parseMarkedReadIds(payload: JSONObject): List<String> {
        val ids = payload.optJSONArray("notification_ids") ?: return emptyList()
        val result = mutableListOf<String>()
        for (index in 0 until ids.length()) {
            val value = ids.optString(index).trim()
            if (value.isNotBlank()) result.add(value)
        }
        return result
    }

    fun parseEventTimestampMs(payload: JSONObject): Long {
        val timestampSeconds = payload.optDouble("timestamp", 0.0)
        if (timestampSeconds > 0.0) {
            return (timestampSeconds * 1000.0).toLong()
        }
        return 0L
    }

    private fun parseRecord(item: JSONObject): SoAINotificationRecord? {
        val id = item.optionalString("id") ?: return null
        val type = parseType(item.optionalString("type")) ?: return null
        return parseRecordBody(item, id, type)
    }

    private fun parseRecordBody(
        source: JSONObject,
        id: String,
        type: SoAINotificationType
    ): SoAINotificationRecord? {
        val title = source.optJSONObject("title")?.let { parseText(it) } ?: return null
        val message = source.optJSONObject("message")?.let { parseText(it) } ?: return null
        val createdAtMs = source.optLong("created_at_ms", 0L).takeIf { it > 0L } ?: return null
        val link = source.optJSONObject("link")?.let { parseLink(it) }
        return SoAINotificationRecord(
            id = id,
            createdAtMs = createdAtMs,
            type = type,
            title = title,
            message = message,
            link = link
        )
    }

    private fun parseText(value: JSONObject): SoAINotificationText? {
        return when (value.optionalString("text_type")) {
            "text" -> SoAINotificationText(
                text = value.optionalString("text") ?: return null,
                template = null,
                params = emptyMap()
            )
            "template" -> SoAINotificationText(
                text = null,
                template = value.optionalString("template") ?: return null,
                params = parseParams(value.optJSONObject("params") ?: JSONObject())
            )
            else -> null
        }
    }

    private fun parseParams(value: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val names = value.names() ?: return result
        for (index in 0 until names.length()) {
            val key = names.optString(index).trim()
            val text = value.optString(key).trim()
            if (key.isNotBlank() && text.isNotBlank()) {
                result[key] = text
            }
        }
        return result
    }

    private fun parseLink(value: JSONObject): SoAINotificationLink? {
        val type = value.optionalString("link_type") ?: return null
        val linkValue = value.optionalString("value") ?: return null
        if (type !in setOf("url", "conversation", "automation_run")) return null
        return SoAINotificationLink(type = type, value = linkValue)
    }

    private fun parseType(value: String?): SoAINotificationType? {
        return when (value) {
            "info" -> SoAINotificationType.INFO
            "success" -> SoAINotificationType.SUCCESS
            "warning" -> SoAINotificationType.WARNING
            "error" -> SoAINotificationType.ERROR
            else -> null
        }
    }
}
