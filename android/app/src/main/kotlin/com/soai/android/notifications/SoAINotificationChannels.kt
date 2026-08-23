// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.soai.android.R

object SoAINotificationChannels {
    const val USER_CHANNEL_ID = "soai_notifications"
    const val CONNECTION_CHANNEL_ID = "soai_connection"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val userChannel = NotificationChannel(
            USER_CHANNEL_ID,
            context.getString(R.string.android_notifications_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        userChannel.description = context.getString(R.string.android_notifications_channel_description)
        val connectionChannel = NotificationChannel(
            CONNECTION_CHANNEL_ID,
            context.getString(R.string.android_notifications_connection_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        connectionChannel.description = context.getString(R.string.android_notifications_connection_channel_description)
        manager.createNotificationChannel(userChannel)
        manager.createNotificationChannel(connectionChannel)
    }

    fun notificationsAllowed(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun channelAllowed(context: Context, channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(channelId) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
