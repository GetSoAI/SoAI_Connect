// Notification preferences and Android permission/settings recovery for ui/NotificationSettingsController.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivitySettingsBinding
import com.soai.android.notifications.SoAINotificationChannels
import com.soai.android.notifications.SoAINotificationPresenter
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.SoAISessionCookies

internal class NotificationSettingsController(
    private val activity: SettingsActivity,
    private val binding: ActivitySettingsBinding,
    private val prefs: AppPreferences,
    private val dialogs: LifecycleDialogRegistry,
    private val showMessage: (String) -> Unit,
    private val requestPermission: () -> Unit
) {
    fun bind() {
        binding.androidNotificationsSwitch.setOnCheckedChangeListener(null)
        binding.androidNotificationsSwitch.isEnabled = !prefs.incognitoMode
        binding.androidNotificationsSwitch.isChecked = prefs.androidNotificationsEnabled && !prefs.incognitoMode
        binding.androidNotificationsSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !permissionGranted()) requestPermission()
            else setEnabled(enabled)
        }
        refresh()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted && !prefs.incognitoMode) setEnabled(true)
        else {
            bind()
            showMessage(activity.getString(R.string.permission_denied))
        }
    }

    fun refresh() {
        val blockedChannel = when {
            !SoAINotificationChannels.channelAllowed(activity, SoAINotificationChannels.USER_CHANNEL_ID) ->
                SoAINotificationChannels.USER_CHANNEL_ID
            !SoAINotificationChannels.channelAllowed(activity, SoAINotificationChannels.CONNECTION_CHANNEL_ID) ->
                SoAINotificationChannels.CONNECTION_CHANNEL_ID
            else -> null
        }
        val androidBlocked = !permissionGranted() || !SoAINotificationChannels.notificationsAllowed(activity)
        val serverUrl = prefs.serverUrl
        val authenticated = serverUrl != null && SoAISessionCookies.hasAuthentication(
            serverUrl, CookieManager.getInstance().getCookie(serverUrl)
        )
        binding.androidNotificationsDescription.text = when {
            prefs.incognitoMode -> activity.getString(R.string.incognito_mode)
            prefs.androidNotificationsEnabled && (androidBlocked || blockedChannel != null) -> {
                val channelTitle = if (blockedChannel == SoAINotificationChannels.CONNECTION_CHANNEL_ID) {
                    R.string.android_notifications_connection_channel_name
                } else R.string.android_notifications_channel_name
                activity.getString(channelTitle) + "\n" + activity.getString(R.string.notifications_blocked_settings)
            }
            prefs.androidNotificationsEnabled && !authenticated -> activity.getString(R.string.notifications_sign_in_required)
            else -> activity.getString(R.string.android_notifications_description)
        }
        binding.androidNotificationsDescription.setOnClickListener {
            val intent = if (!androidBlocked && blockedChannel != null) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, blockedChannel)
            } else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            openSettings(intent)
        }
        SoAINotificationServiceController.sync(activity)
    }

    private fun setEnabled(enabled: Boolean) {
        if (prefs.incognitoMode) {
            bind()
            return
        }
        if (prefs.androidNotificationsEnabled != enabled) {
            if (!enabled) SoAINotificationPresenter.cancelKnown(activity, prefs.notificationSeenIds)
            prefs.clearNativeNotificationState()
            prefs.androidNotificationsEnabled = enabled
        }
        bind()
        if (!enabled) return
        dialogs.show(MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.android_notifications_background_title)
            .setMessage(R.string.android_notifications_background_message)
            .setPositiveButton(R.string.settings_title) { _, _ ->
                openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            .setNegativeButton(R.string.ok, null))
    }

    private fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openSettings(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: SecurityException) {
            showMessage(activity.getString(R.string.permission_denied))
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${activity.packageName}".toUri()))
            } catch (_: SecurityException) {
                showMessage(activity.getString(R.string.permission_denied))
            } catch (_: ActivityNotFoundException) {
                showMessage(activity.getString(R.string.error_opening_link))
            }
        }
    }
}
