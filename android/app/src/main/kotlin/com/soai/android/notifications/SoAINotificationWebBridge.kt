// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.content.Context
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.util.Log
import com.soai.android.R
import com.soai.android.SoAIApplication
import com.soai.android.data.AppPreferences
import java.security.SecureRandom
import org.json.JSONObject

class SoAINotificationWebBridge(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences.getInstance(appContext)
    val token = createToken()

    @JavascriptInterface
    fun showNotification(payloadJson: String) {
        if (payloadJson.length > MAX_PAYLOAD_CHARACTERS) {
            Log.w(TAG, "Ignoring oversized notification bridge payload")
            return
        }
        val payload = try {
            JSONObject(payloadJson)
        } catch (exception: Exception) {
            Log.w(TAG, "Ignoring malformed notification bridge payload", exception)
            return
        }
        if (payload.optionalString("token") != token) {
            return
        }
        if (!prefs.androidNotificationsEnabled || prefs.incognitoMode) {
            return
        }
        if (SoAIApplication.instance.isAppForegrounded()) {
            return
        }
        val message = payload.optionalString("message")?.take(MAX_MESSAGE_CHARACTERS) ?: return
        val type = when (payload.optionalString("type")) {
            "success" -> SoAINotificationType.SUCCESS
            "warning" -> SoAINotificationType.WARNING
            "error" -> SoAINotificationType.ERROR
            else -> SoAINotificationType.INFO
        }
        val display = SoAINotificationDisplay(
            id = "frontend-${SystemClock.elapsedRealtimeNanos()}",
            title = appContext.getString(R.string.notification_generic_title),
            message = message,
            type = type,
            route = null,
            externalUrl = null
        )
        SoAINotificationPresenter.show(appContext, display)
    }

    private fun createToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val TAG = "SoAINotificationBridge"
        private const val TOKEN_BYTES = 24
        private const val MAX_PAYLOAD_CHARACTERS = 16_384
        private const val MAX_MESSAGE_CHARACTERS = 2_000
    }
}
