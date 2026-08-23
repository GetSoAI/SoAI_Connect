// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object WebViewDataCleaner {
    private val webViews = mutableSetOf<WebView>()
    private val cleanupCallbacks = mutableListOf<() -> Unit>()
    private var sessionCleanupInProgress = false

    fun register(webView: WebView) {
        webViews.add(webView)
    }

    fun unregister(webView: WebView) {
        webViews.remove(webView)
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
        clearCache()
        webViews.toList().forEach { webView ->
            webView.clearHistory()
            webView.clearFormData()
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
