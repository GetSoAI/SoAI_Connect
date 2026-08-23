// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.soai.android.network.ServerOrigin
import kotlinx.coroutines.launch
import org.json.JSONObject

class SoAINotificationLaunchRouter(
    private val activity: AppCompatActivity,
    private val openExternalUrl: (Uri) -> Unit
) {

    fun consume(intent: Intent?, serverUrl: String): String {
        markOpenedNotificationRead(intent)
        openExternalNotificationUrl(intent)
        return resolveLaunchUrl(intent, serverUrl)
    }

    fun dispatch(webView: WebView, intent: Intent?, serverUrl: String) {
        val launchUrl = consume(intent, serverUrl)
        val loadedUrl = webView.url
        if (loadedUrl != null && ServerOrigin.isSameOrigin(serverUrl.toUri(), loadedUrl.toUri())) {
            navigateLoadedPage(webView, launchUrl)
        } else {
            webView.loadUrl(launchUrl)
        }
    }

    private fun navigateLoadedPage(webView: WebView, launchUrl: String) {
        val route = launchUrl.substringAfter('#', "")
        if (route.isEmpty()) return
        val fragmentLiteral = JSONObject.quote("#$route")
        webView.evaluateJavascript("window.location.hash=$fragmentLiteral;", null)
    }

    private fun markOpenedNotificationRead(intent: Intent?) {
        val notificationId = intent?.getStringExtra(SoAINotificationDeepLinks.EXTRA_NOTIFICATION_ID)
            ?.trim()
            ?.ifBlank { null }
            ?: return
        intent.removeExtra(SoAINotificationDeepLinks.EXTRA_NOTIFICATION_ID)
        activity.lifecycleScope.launch {
            SoAINotificationReadMarker.markRead(activity, notificationId)
        }
    }

    private fun openExternalNotificationUrl(intent: Intent?) {
        val externalUrl = intent?.getStringExtra(SoAINotificationDeepLinks.EXTRA_EXTERNAL_URL)
            ?.trim()
            ?.ifBlank { null }
            ?: return
        intent.removeExtra(SoAINotificationDeepLinks.EXTRA_EXTERNAL_URL)
        val uri = externalUrl.toUri()
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            openExternalUrl(uri)
        }
    }

    private fun resolveLaunchUrl(intent: Intent?, serverUrl: String): String {
        val route = intent?.getStringExtra(SoAINotificationDeepLinks.EXTRA_ROUTE)
            ?.trim()
            ?.removePrefix("#")
            ?.ifBlank { null }
            ?: return serverUrl
        intent.removeExtra(SoAINotificationDeepLinks.EXTRA_ROUTE)
        return "${serverUrl.substringBefore("#").trimEnd('/')}#$route"
    }
}
