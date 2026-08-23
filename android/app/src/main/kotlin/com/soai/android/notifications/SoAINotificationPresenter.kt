// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soai.android.R
import com.soai.android.ui.MainActivity

object SoAINotificationPresenter {

    fun canPost(context: Context): Boolean {
        if (!notificationsPermissionGranted(context)) return false
        return SoAINotificationChannels.channelAllowed(context, SoAINotificationChannels.USER_CHANNEL_ID)
    }

    fun canRunListener(context: Context): Boolean {
        if (!notificationsPermissionGranted(context)) return false
        return SoAINotificationChannels.channelAllowed(
            context,
            SoAINotificationChannels.CONNECTION_CHANNEL_ID
        ) && SoAINotificationChannels.channelAllowed(context, SoAINotificationChannels.USER_CHANNEL_ID)
    }

    private fun notificationsPermissionGranted(context: Context): Boolean {
        if (!SoAINotificationChannels.notificationsAllowed(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun buildForegroundNotification(context: Context) =
        NotificationCompat.Builder(context, SoAINotificationChannels.CONNECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.android_notifications_foreground_title))
            .setContentText(context.getString(R.string.android_notifications_foreground_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(foregroundContentIntent(context))
            .build()

    @SuppressLint("MissingPermission")
    fun show(context: Context, display: SoAINotificationDisplay) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, SoAINotificationChannels.USER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(display.title)
            .setContentText(display.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(display.message))
            .setPriority(priority(display.type))
            .setAutoCancel(true)
            .setContentIntent(SoAINotificationDeepLinks.pendingIntent(context, display))
            .build()
        NotificationManagerCompat.from(context).notify(display.id, display.id.hashCode(), notification)
    }

    fun cancel(context: Context, notificationId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId, notificationId.hashCode())
    }

    fun cancelKnown(context: Context, notificationIds: Collection<String>) {
        notificationIds.forEach { notificationId ->
            if (notificationId.isNotBlank()) {
                cancel(context, notificationId)
            }
        }
    }

    private fun priority(type: SoAINotificationType): Int {
        return when (type) {
            SoAINotificationType.ERROR -> NotificationCompat.PRIORITY_HIGH
            SoAINotificationType.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            SoAINotificationType.SUCCESS -> NotificationCompat.PRIORITY_DEFAULT
            SoAINotificationType.INFO -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    private fun foregroundContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            FOREGROUND_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val FOREGROUND_REQUEST_CODE = 4601
}
