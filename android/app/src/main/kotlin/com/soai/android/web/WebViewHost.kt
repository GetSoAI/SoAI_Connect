// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView

class WebViewHost(
    val webView: WebView,
    private val progressBar: ProgressBar,
    private val errorLayout: View,
    private val errorText: TextView,
    private val retryButton: View
) {
    init {
        WebViewDataCleaner.register(webView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(incognitoMode: Boolean, onRetry: () -> Unit) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.settings.apply {
            javaScriptEnabled = true
            safeBrowsingEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = if (incognitoMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = true
        }

        retryButton.setOnClickListener {
            showContent()
            onRetry()
        }
    }

    fun showContent() {
        errorLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    fun showError(message: String) {
        errorLayout.visibility = View.VISIBLE
        webView.visibility = View.GONE
        errorText.text = message
    }

    fun showLoadStarted() {
        progressBar.isIndeterminate = true
        progressBar.visibility = View.VISIBLE
        errorLayout.visibility = View.GONE
    }

    fun setLoadProgress(progress: Int) {
        progressBar.isIndeterminate = false
        progressBar.progress = progress
        progressBar.visibility = if (progress == PROGRESS_COMPLETE) View.GONE else View.VISIBLE
    }

    fun hideLoadProgress() {
        progressBar.visibility = View.GONE
    }

    fun hardReload() {
        showContent()
        webView.clearCache(true)
        webView.reload()
    }

    fun destroy() {
        WebViewDataCleaner.unregister(webView)
        webView.stopLoading()
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.clearHistory()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    companion object {
        private const val PROGRESS_COMPLETE = 100
    }
}
