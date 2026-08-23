// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.soai.android.ui.MainActivity

object SoAINotificationDeepLinks {
    const val EXTRA_ROUTE = "com.soai.android.extra.ROUTE"
    const val EXTRA_NOTIFICATION_ID = "com.soai.android.extra.NOTIFICATION_ID"
    const val EXTRA_EXTERNAL_URL = "com.soai.android.extra.EXTERNAL_URL"

    fun pendingIntent(context: Context, display: SoAINotificationDisplay): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.Builder()
                .scheme(INTERNAL_SCHEME)
                .authority(INTERNAL_AUTHORITY)
                .appendPath(display.id)
                .build()
            putExtra(EXTRA_NOTIFICATION_ID, display.id)
            if (display.externalUrl != null) {
                putExtra(EXTRA_EXTERNAL_URL, display.externalUrl)
            } else {
                putExtra(EXTRA_ROUTE, display.route)
            }
        }
        return PendingIntent.getActivity(
            context,
            display.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val INTERNAL_SCHEME = "soai-connect"
    private const val INTERNAL_AUTHORITY = "notification"
}
