// WebUI page loading, certificate trust and lifecycle for ui/MainWebPageOwner.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.AndroidDeviceIdentityCookie
import com.soai.android.web.ExternalNavigation
import com.soai.android.web.PopupWebViewController
import com.soai.android.web.SoAIWebPageOwner
import com.soai.android.web.WebViewDataCleaner
import com.soai.android.web.WebViewHost
import com.soai.android.web.WebSessionDataOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class MainWebPageOwner(
    private val activity: MainActivity,
    private val prefs: AppPreferences,
    private val dialogs: LifecycleDialogRegistry,
    private val popupController: PopupWebViewController,
    private val trustController: ServerTrustController,
    private val mainHost: () -> WebViewHost,
    private val recreateMainWebView: (Boolean) -> Unit,
    private val showMessage: (String) -> Unit
) : SoAIWebPageOwner, WebSessionDataOwner {
    private var rendererRecoveryAttempted = false
    private var pageLoadGeneration = 0L
    private var pageLoadJob: Job? = null

    init {
        WebViewDataCleaner.registerSessionOwner(this)
    }

    override fun onSessionDataClearing() {
        cancelPageLoad()
        popupController.dismissAll()
        trustController.resetSessionTrust()
        mainHost().webView.stopLoading()
    }

    fun destroy() {
        cancelPageLoad()
        WebViewDataCleaner.unregisterSessionOwner(this)
    }

    fun cancelPageLoad() {
        pageLoadGeneration += 1L
        pageLoadJob?.cancel()
        pageLoadJob = null
    }

    fun prepareLoad(onReady: (String) -> Unit) {
        cancelPageLoad()
        val generation = pageLoadGeneration
        val host = mainHost()
        WebViewDataCleaner.runWhenSessionDataReady {
            if (!isCurrentPageLoad(generation, host)) return@runWhenSessionDataReady
            val serverUrl = prefs.serverUrl ?: return@runWhenSessionDataReady
            pageLoadJob = activity.lifecycleScope.launch {
                host.showLoadStarted()
                if (!trustController.preparePageLoad(host)) {
                    if (isCurrentPageLoad(generation, host)) host.hideLoadProgress()
                    return@launch
                }
                if (!isCurrentPageLoad(generation, host)) return@launch
                AndroidDeviceIdentityCookie.install(CookieManager.getInstance(), serverUrl, prefs.deviceIdentity()) { installed ->
                    if (!isCurrentPageLoad(generation, host)) return@install
                    if (!installed) {
                        host.hideLoadProgress()
                        host.showError(activity.getString(R.string.device_identity_cookie_failed))
                    } else {
                        host.hideLoadProgress()
                        host.showContent()
                        onReady(serverUrl)
                    }
                }
            }
        }
    }

    private fun isCurrentPageLoad(generation: Long, host: WebViewHost): Boolean {
        return generation == pageLoadGeneration && host === mainHost() && !activity.isFinishing && !activity.isDestroyed
    }

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
