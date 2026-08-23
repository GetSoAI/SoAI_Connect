// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import org.json.JSONObject

enum class SoAINotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class SoAINotificationText(
    val text: String?,
    val template: String?,
    val params: Map<String, String>
)

data class SoAINotificationLink(
    val type: String,
    val value: String
)

data class SoAINotificationRecord(
    val id: String,
    val createdAtMs: Long,
    val type: SoAINotificationType,
    val title: SoAINotificationText,
    val message: SoAINotificationText,
    val link: SoAINotificationLink?
)

data class SoAINotificationDisplay(
    val id: String,
    val title: String,
    val message: String,
    val type: SoAINotificationType,
    val route: String?,
    val externalUrl: String?
)

internal fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).trim().ifBlank { null }
}
