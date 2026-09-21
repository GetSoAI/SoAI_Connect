// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SoAINotificationRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        SoAINotificationServiceController.sync(context)
    }
}
