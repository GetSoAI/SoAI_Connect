// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.soai.android.data.AppPreferences
import com.soai.android.network.ServerOrigin
import org.json.JSONObject

class SoAINotificationBridgeController(
    private val activity: AppCompatActivity,
    private val prefs: AppPreferences
) {
    private var bridge: SoAINotificationWebBridge? = null
    private var attachedWebView: WebView? = null

    fun attach(webView: WebView) {
        detach()
        val newBridge = SoAINotificationWebBridge(activity)
        bridge = newBridge
        attachedWebView = webView
        webView.addJavascriptInterface(newBridge, BRIDGE_NAME)
    }

    fun injectToken(webView: WebView) {
        val token = bridge?.token ?: return
        val serverUrl = prefs.serverUrl ?: return
        val loadedUrl = webView.url ?: return
        if (!ServerOrigin.isSameOrigin(serverUrl.toUri(), loadedUrl.toUri())) return
        webView.evaluateJavascript(
            "window.SoAIAndroidNotificationsToken=${JSONObject.quote(token)};",
            null
        )
    }

    fun detach() {
        attachedWebView?.removeJavascriptInterface(BRIDGE_NAME)
        attachedWebView = null
        bridge = null
    }

    companion object {
        private const val BRIDGE_NAME = "SoAIAndroidNotifications"
    }
}
