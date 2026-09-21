// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.ExternalNavigation
import com.soai.android.web.PopupWebViewController
import com.soai.android.web.SoAIWebPageOwner
import com.soai.android.web.WebViewHost

internal class MainWebPageOwner(
    private val activity: MainActivity,
    private val prefs: AppPreferences,
    private val dialogs: LifecycleDialogRegistry,
    private val popupController: PopupWebViewController,
    private val trustController: ServerTrustController,
    private val mainHost: () -> WebViewHost,
    private val recreateMainWebView: (Boolean) -> Unit,
    private val showMessage: (String) -> Unit
) : SoAIWebPageOwner {
    private var rendererRecoveryAttempted = false

    override val serverUri: Uri?
        get() = prefs.serverUrl?.let(Uri::parse)

    override val incognitoMode: Boolean
        get() = prefs.incognitoMode

    override fun onPageFinished(webView: WebView) {
        if (!prefs.incognitoMode) prefs.webCachePopulated = true
        val host = mainHost()
        if (webView != host.webView) return
        rendererRecoveryAttempted = false
        SoAINotificationServiceController.sync(activity)
    }

    override fun onRenderProcessCrashed(webView: WebView) {
        if (popupController.handleRenderProcessCrash(webView)) {
            showMessage(activity.getString(R.string.webview_crashed))
            return
        }
        if (webView != mainHost().webView) return
        if (rendererRecoveryAttempted) {
            recreateMainWebView(false)
            mainHost().showError(activity.getString(R.string.webview_crashed))
            return
        }
        rendererRecoveryAttempted = true
        recreateMainWebView(true)
    }

    override fun handleServerSslError(
        host: WebViewHost,
        handler: SslErrorHandler,
        error: SslError
    ) {
        trustController.handleSslError(host, handler, error)
    }

    override fun openExternalUrl(uri: Uri) {
        ExternalNavigation.openInExternalApp(activity, uri) {
            showMessage(activity.getString(R.string.error_opening_link))
        }
    }

    override fun openNonHttpUrl(uri: Uri) {
        ExternalNavigation.confirmAndOpenNonHttp(activity, dialogs, uri) {
            showMessage(activity.getString(R.string.error_opening_link))
        }
    }

    override fun onExternalNavigationFromBlankPage(webView: WebView) {
        popupController.close(webView)
    }

    fun onTrustedCertificateReset() {
        mainHost().webView.clearSslPreferences()
        popupController.clearSslPreferences()
        popupController.dismissAll()
        trustController.resetSessionTrust()
        recreateMainWebView(true)
    }
}
