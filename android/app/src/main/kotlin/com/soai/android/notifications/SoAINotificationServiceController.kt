// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import com.soai.android.data.AppPreferences
import com.soai.android.web.SoAISessionCookies

object SoAINotificationServiceController {

    fun sync(context: Context) {
        val prefs = AppPreferences.getInstance(context)
        val serverUrl = prefs.serverUrl
        val cookieHeader = serverUrl?.let { url -> CookieManager.getInstance().getCookie(url) }
        if (
            prefs.androidNotificationsEnabled &&
            !prefs.incognitoMode &&
            prefs.hasConnectedServer() &&
            serverUrl != null &&
            SoAISessionCookies.hasAuthentication(serverUrl, cookieHeader)
        ) {
            start(context)
            return
        }
        stop(context)
    }

    fun restart(context: Context) {
        stop(context)
        sync(context)
    }

    fun start(context: Context) {
        val intent = Intent(context, SoAINotificationListenerService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Android rejected SoAI notification listener start", exception)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, SoAINotificationListenerService::class.java))
    }

    private const val TAG = "SoAINotificationCtl"
}
