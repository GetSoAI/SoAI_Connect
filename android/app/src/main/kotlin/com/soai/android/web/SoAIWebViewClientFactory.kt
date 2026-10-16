// WebView navigation, page failures and certificate callbacks for web/SoAIWebViewClientFactory.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.soai.android.R
import com.soai.android.network.ServerOrigin

interface SoAIWebPageOwner {
    val serverUri: Uri?
    val incognitoMode: Boolean
    fun onPageFinished(webView: WebView)
    fun onRenderProcessCrashed(webView: WebView)
    fun handleServerSslError(host: WebViewHost, handler: SslErrorHandler, error: SslError)
    fun openExternalUrl(uri: Uri)
    fun openNonHttpUrl(uri: Uri)
    fun onExternalNavigationFromBlankPage(webView: WebView)
}

object SoAIWebViewClientFactory {

    fun create(context: Context, host: WebViewHost, owner: SoAIWebPageOwner): WebViewClient {
        return object : WebViewClient() {
            private val pageLoad = WebPageLoadState()

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (pageLoad.onStarted()) host.showLoadStarted()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                host.hideLoadProgress()
                if (!owner.incognitoMode) {
                    CookieManager.getInstance().flush()
                }
                val failureResource = pageLoad.onFinished()
                if (failureResource == null) owner.onPageFinished(host.webView)
                else host.showError(context.getString(failureResource))
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                val failureResource = WebPageFailure.messageResource(error?.errorCode)
                pageLoad.onFailure(failureResource)
                host.hideLoadProgress()
                host.showError(context.getString(failureResource))
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame != true || errorResponse == null) return
                val failureResource = WebPageFailure.httpMessageResource(errorResponse.statusCode)
                pageLoad.onFailure(failureResource)
                host.hideLoadProgress()
                host.showError(context.getString(failureResource))
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                host.showError(context.getString(R.string.webview_crashed))
                owner.onRenderProcessCrashed(host.webView)
                return true
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val sslHandler = handler ?: return
                if (error == null) {
                    sslHandler.cancel()
                    return
                }
                owner.handleServerSslError(host, sslHandler, error)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request?.isForMainFrame != true) return false

                val url = request.url ?: return false
                val server = owner.serverUri ?: return false

                val scheme = url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    if (ServerOrigin.isSameOrigin(url, server)) return false
                    owner.openExternalUrl(url)
                    dismissBlankSourcePage(view)
                    return true
                }

                owner.openNonHttpUrl(url)
                dismissBlankSourcePage(view)
                return true
            }

            private fun dismissBlankSourcePage(view: WebView?) {
                if (view == null) return
                if (view.url != null) return
                owner.onExternalNavigationFromBlankPage(view)
            }
        }
    }
}
