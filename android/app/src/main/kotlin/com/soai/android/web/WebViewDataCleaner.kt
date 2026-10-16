// WebView session data and certificate-decision cleanup for web/WebViewDataCleaner.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.soai.android.network.SessionCertificateTrust

internal interface WebSessionDataOwner {
    fun onSessionDataClearing()
}

object WebViewDataCleaner {
    private val webViews = mutableSetOf<WebView>()
    private val sessionOwners = mutableSetOf<WebSessionDataOwner>()
    private val cleanupCallbacks = mutableListOf<() -> Unit>()
    private var sessionCleanupInProgress = false

    fun register(webView: WebView) {
        webViews.add(webView)
    }

    fun unregister(webView: WebView) {
        webViews.remove(webView)
    }

    internal fun registerSessionOwner(owner: WebSessionDataOwner) {
        sessionOwners.add(owner)
    }

    internal fun unregisterSessionOwner(owner: WebSessionDataOwner) {
        sessionOwners.remove(owner)
    }

    fun clearCache() {
        webViews.toList().forEach { webView ->
            webView.clearCache(true)
        }
    }

    fun clearSessionData(onComplete: (() -> Unit)? = null) {
        if (onComplete != null) cleanupCallbacks.add(onComplete)
        if (sessionCleanupInProgress) return
        sessionCleanupInProgress = true
        sessionOwners.toList().forEach { owner -> owner.onSessionDataClearing() }
        SessionCertificateTrust.clear()
        clearCache()
        webViews.toList().forEach { webView ->
            webView.clearHistory()
            webView.clearFormData()
            webView.clearSslPreferences()
        }
        WebStorage.getInstance().deleteAllData()
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            sessionCleanupInProgress = false
            val callbacks = cleanupCallbacks.toList()
            cleanupCallbacks.clear()
            callbacks.forEach { callback -> callback.invoke() }
        }
    }

    fun runWhenSessionDataReady(action: () -> Unit) {
        if (sessionCleanupInProgress) {
            cleanupCallbacks.add(action)
        } else {
            action()
        }
    }
}
