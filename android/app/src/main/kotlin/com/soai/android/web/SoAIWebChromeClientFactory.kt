// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.net.Uri
import android.os.Message
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

object SoAIWebChromeClientFactory {

    fun create(
        host: WebViewHost,
        popups: PopupWebViewController,
        fileChooser: WebFileChooser,
        permissions: WebPermissionMediator,
        onTitleChanged: (String) -> Unit = {}
    ): WebChromeClient {
        return object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                host.setLoadProgress(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let(onTitleChanged)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val message = resultMsg ?: return false
                return popups.open(message, isUserGesture)
            }

            override fun onCloseWindow(window: WebView?) {
                val webView = window ?: return
                popups.close(webView)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                val callback = filePathCallback ?: return false
                val params = fileChooserParams ?: return false
                fileChooser.show(webView, callback, params)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val permissionRequest = request ?: return
                permissions.handleRequest(permissionRequest)
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                permissions.handleCanceled(request)
            }
        }
    }
}
