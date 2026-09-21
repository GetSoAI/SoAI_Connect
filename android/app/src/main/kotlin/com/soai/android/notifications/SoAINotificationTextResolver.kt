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

    private fun resolve(context: Context, value: SoAINotificationText, fallbackResource: Int): String {
        value.text?.let { return it }
        val text = templateText(value.template, value.params) ?: return context.getString(fallbackResource)
        return if (text.args.isEmpty()) {
            context.getString(text.resource)
        } else {
            context.getString(text.resource, *text.args.toTypedArray())
        }
    }

    internal fun templateText(template: String?, params: Map<String, String>): SoAINotificationTemplateText? {
        fun text(resource: Int, vararg names: String) =
            SoAINotificationTemplateText(resource, names.map { name -> params[name].orEmpty() })
        return when (template) {
            "tool_approval_required_title" -> text(R.string.notification_tool_approval_title)
            "tool_approval_required_message" -> text(R.string.notification_tool_approval_message, "toolName")
            "ask_user_required_title" -> text(R.string.notification_ask_user_title)
            "ask_user_required_message" -> text(R.string.notification_ask_user_message)
            "vault_secret_request_required_title" -> text(R.string.notification_secret_request_title)
            "vault_secret_request_required_message" -> text(R.string.notification_secret_request_message)
            "automation_completed_title" -> text(R.string.notification_automation_completed_title)
            "automation_completed_message" -> text(R.string.notification_automation_completed_message, "automationTitle")
            "automation_completed_message_generic" -> text(R.string.notification_automation_completed_message_generic)
            "automation_failed_title" -> text(R.string.notification_automation_failed_title)
            "automation_failed_message" -> text(R.string.notification_automation_failed_message, "automationTitle")
            "automation_failed_message_generic" -> text(R.string.notification_automation_failed_message_generic)
            "automation_failed_message_with_status" -> text(R.string.notification_automation_failed_message_with_status, "automationTitle", "statusMessage")
            "automation_failed_message_with_status_generic" -> text(R.string.notification_automation_failed_message_with_status_generic, "statusMessage")
            "mail_new_title" -> text(R.string.notification_mail_new_title, "accountLabel")
            "mail_new_message" -> text(R.string.notification_mail_new_message, "from", "subject")
            "mail_sync_failure_title" -> text(R.string.notification_mail_sync_failure_title, "accountLabel")
            "mail_sync_failure_message" -> text(R.string.notification_mail_sync_failure_message, "reason")
            "calendar_invite_update_title" -> text(R.string.notification_calendar_invite_update_title, "accountLabel")
            "calendar_invite_update_message" -> text(R.string.notification_calendar_invite_update_message, "summary", "start")
            "calendar_reminder_due_title" -> text(R.string.notification_calendar_reminder_due_title, "accountLabel")
            "calendar_reminder_due_message" -> text(R.string.notification_calendar_reminder_due_message, "summary", "start")
            "calendar_reminder_missed_title" -> text(R.string.notification_calendar_reminder_missed_title, "accountLabel")
            "calendar_reminder_missed_message" -> text(R.string.notification_calendar_reminder_missed_message, "summary", "start")
            "calendar_sync_failure_title" -> text(R.string.notification_calendar_sync_failure_title, "accountLabel")
            "calendar_sync_failure_message" -> text(R.string.notification_calendar_sync_failure_message, "reason")
            "openai_quota_exhausted_title" -> text(R.string.notification_openai_quota_exhausted_title, "keyLabel")
            "openai_quota_exhausted_message" -> text(R.string.notification_openai_quota_exhausted_message, "keyLabel", "windowLabel")
            "api_rate_limit_applied_title" -> text(R.string.notification_api_rate_limit_applied_title, "scopeLabel")
            "api_rate_limit_applied_message" -> text(R.string.notification_api_rate_limit_applied_message, "scopeLabel", "failureCount")
            "backup_operation_failed_title" -> text(R.string.notification_backup_operation_failed_title, "operationLabel")
            "backup_operation_failed_message" -> text(R.string.notification_backup_operation_failed_message, "operationLabel", "failureCount")
            "low_disk_space_title" -> text(R.string.notification_low_disk_space_title, "operationLabel")
            "low_disk_space_message" -> text(R.string.notification_low_disk_space_message, "operationLabel", "deficit")
            "messaging_account_degraded_title" -> text(R.string.notification_messaging_account_degraded_title)
            "messaging_account_degraded_message" -> text(R.string.notification_messaging_account_degraded_message, "providerLabel", "accountLabel")
            "messaging_account_recovered_title" -> text(R.string.notification_messaging_account_recovered_title)
            "messaging_account_recovered_message" -> text(R.string.notification_messaging_account_recovered_message, "providerLabel", "accountLabel")
            "plugin_circuit_breaker_tripped_title" -> text(R.string.notification_plugin_circuit_breaker_tripped_title)
            "plugin_circuit_breaker_tripped_message" -> text(R.string.notification_plugin_circuit_breaker_tripped_message, "pluginName")
            "security_login_throttle_title" -> text(R.string.notification_security_login_throttle_title)
            "security_login_throttle_message" -> text(R.string.notification_security_login_throttle_message, "attemptCount", "windowMinutes")
            else -> null
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

internal data class SoAINotificationTemplateText(
    val resource: Int,
    val args: List<String>
)
