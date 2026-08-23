// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.content.Context
import com.soai.android.R
import java.net.URLEncoder

object SoAINotificationTextResolver {

    fun resolveDisplay(context: Context, record: SoAINotificationRecord): SoAINotificationDisplay {
        val title = resolve(context, record.title, R.string.notification_generic_title)
        val message = resolve(context, record.message, R.string.notification_generic_message)
        val link = record.link
        val route = when (link?.type) {
            "conversation" -> "chat/conversation/${encodeSegment(link.value)}"
            "automation_run" -> "automation?run_id=${encodeQuery(link.value)}"
            "url" -> resolveInternalRoute(link.value)
            else -> null
        }
        val externalUrl = if (link?.type == "url" && route == null) link.value else null
        return SoAINotificationDisplay(
            id = record.id,
            title = title,
            message = message,
            type = record.type,
            route = route,
            externalUrl = externalUrl
        )
    }

    fun resolve(context: Context, value: SoAINotificationText, fallbackResource: Int): String {
        value.text?.let { return it }
        val params = value.params
        return when (value.template) {
            "tool_approval_required_title" -> context.getString(R.string.notification_tool_approval_title)
            "tool_approval_required_message" -> context.getString(R.string.notification_tool_approval_message, params["toolName"].orEmpty())
            "ask_user_required_title" -> context.getString(R.string.notification_ask_user_title)
            "ask_user_required_message" -> context.getString(R.string.notification_ask_user_message)
            "secret_request_required_title" -> context.getString(R.string.notification_secret_request_title)
            "secret_request_required_message" -> context.getString(R.string.notification_secret_request_message)
            "automation_completed_title" -> context.getString(R.string.notification_automation_completed_title)
            "automation_completed_message" -> context.getString(R.string.notification_automation_completed_message, params["automationTitle"].orEmpty())
            "automation_completed_message_generic" -> context.getString(R.string.notification_automation_completed_message_generic)
            "automation_failed_title" -> context.getString(R.string.notification_automation_failed_title)
            "automation_failed_message" -> context.getString(R.string.notification_automation_failed_message, params["automationTitle"].orEmpty())
            "automation_failed_message_generic" -> context.getString(R.string.notification_automation_failed_message_generic)
            "automation_failed_message_with_status" -> context.getString(R.string.notification_automation_failed_message_with_status, params["automationTitle"].orEmpty(), params["statusMessage"].orEmpty())
            "automation_failed_message_with_status_generic" -> context.getString(R.string.notification_automation_failed_message_with_status_generic, params["statusMessage"].orEmpty())
            "mail_new_title" -> context.getString(R.string.notification_mail_new_title, params["accountLabel"].orEmpty())
            "mail_new_message" -> context.getString(R.string.notification_mail_new_message, params["from"].orEmpty(), params["subject"].orEmpty())
            "mail_sync_failure_title" -> context.getString(R.string.notification_mail_sync_failure_title, params["accountLabel"].orEmpty())
            "mail_sync_failure_message" -> context.getString(R.string.notification_mail_sync_failure_message, params["reason"].orEmpty())
            "calendar_invite_update_title" -> context.getString(R.string.notification_calendar_invite_update_title, params["accountLabel"].orEmpty())
            "calendar_invite_update_message" -> context.getString(R.string.notification_calendar_invite_update_message, params["summary"].orEmpty(), params["start"].orEmpty())
            "calendar_reminder_due_title" -> context.getString(R.string.notification_calendar_reminder_due_title, params["accountLabel"].orEmpty())
            "calendar_reminder_due_message" -> context.getString(R.string.notification_calendar_reminder_due_message, params["summary"].orEmpty(), params["start"].orEmpty())
            "calendar_reminder_missed_title" -> context.getString(R.string.notification_calendar_reminder_missed_title, params["accountLabel"].orEmpty())
            "calendar_reminder_missed_message" -> context.getString(R.string.notification_calendar_reminder_missed_message, params["summary"].orEmpty(), params["start"].orEmpty())
            "calendar_sync_failure_title" -> context.getString(R.string.notification_calendar_sync_failure_title, params["accountLabel"].orEmpty())
            "calendar_sync_failure_message" -> context.getString(R.string.notification_calendar_sync_failure_message, params["reason"].orEmpty())
            else -> context.getString(fallbackResource)
        }
    }

    private fun resolveInternalRoute(value: String): String? {
        val trimmed = value.trim()
        val isAbsoluteUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        if (isAbsoluteUrl) return null
        return trimmed.removePrefix("#").ifBlank { null }
    }

    private fun encodeSegment(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private fun encodeQuery(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }
}
