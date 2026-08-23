// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.app.Dialog
import android.os.Message
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebView.WebViewTransport
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.soai.android.R
import com.soai.android.databinding.DialogPopupWebviewBinding
import com.soai.android.ui.ContentWindowInsets
import com.soai.android.ui.SoAIWindowSystemBars

class PopupWebViewController(
    private val activity: AppCompatActivity,
    private val configureHost: (WebViewHost, (String) -> Unit) -> Unit,
    private val onPopupDismissed: (WebView) -> Unit
) {

    private val dialogsByWebView = mutableMapOf<WebView, Dialog>()

    fun open(resultMessage: Message, isUserGesture: Boolean): Boolean {
        if (!isUserGesture || activity.isFinishing || activity.isDestroyed) return false
        val transport = resultMessage.obj as? WebViewTransport ?: return false

        val popup = createPopup()
        transport.webView = popup.host.webView
        resultMessage.sendToTarget()
        popup.dialog.show()
        return true
    }

    fun close(webView: WebView) {
        val dialog = dialogsByWebView[webView] ?: return
        dialog.dismiss()
    }

    fun dismissAll() {
        dialogsByWebView.values.toList().forEach { dialog -> dialog.dismiss() }
        dialogsByWebView.clear()
    }

    fun clearSslPreferences() {
        dialogsByWebView.keys.forEach { webView -> webView.clearSslPreferences() }
    }

    fun pauseAll() {
        dialogsByWebView.keys.forEach { webView -> webView.onPause() }
    }

    fun resumeAll() {
        dialogsByWebView.keys.forEach { webView -> webView.onResume() }
    }

    fun handleRenderProcessCrash(webView: WebView): Boolean {
        val dialog = dialogsByWebView[webView] ?: return false
        dialog.dismiss()
        return true
    }

    private fun createPopup(): Popup {
        val dialog = Dialog(activity, R.style.Theme_SoAI_FullScreenDialog)
        val popupBinding = DialogPopupWebviewBinding.inflate(activity.layoutInflater)
        dialog.setContentView(popupBinding.root)
        dialog.setOnShowListener {
            val popupWindow = requireNotNull(dialog.window)
            SoAIWindowSystemBars.hideStatusBar(popupWindow)
            popupWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        ContentWindowInsets.applyBottom(popupBinding.contentContainer)

        val popupWebView = WebView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        popupBinding.webViewContainer.addView(popupWebView)

        val host = WebViewHost(
            webView = popupWebView,
            progressBar = popupBinding.progressBar,
            errorLayout = popupBinding.errorLayout,
            errorText = popupBinding.errorText,
            retryButton = popupBinding.retryButton
        )
        configureHost(host) { title ->
            popupBinding.toolbar.title = title.ifBlank { activity.getString(R.string.popup_title) }
        }

        popupBinding.toolbar.apply {
            overflowIcon = ContextCompat.getDrawable(activity, R.drawable.ic_overflow_horiz)
            inflateMenu(R.menu.menu_popup)
            setOnMenuItemClickListener { item ->
                if (item.itemId != R.id.menu_close) return@setOnMenuItemClickListener false
                dialog.dismiss()
                true
            }
        }

        dialog.setOnDismissListener {
            dialogsByWebView.remove(host.webView)
            onPopupDismissed(host.webView)
            host.destroy()
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }
            if (host.webView.canGoBack()) {
                host.webView.goBack()
            } else {
                dialog.dismiss()
            }
            true
        }

        dialogsByWebView[host.webView] = dialog
        return Popup(dialog, host)
    }

    private data class Popup(val dialog: Dialog, val host: WebViewHost)
}
