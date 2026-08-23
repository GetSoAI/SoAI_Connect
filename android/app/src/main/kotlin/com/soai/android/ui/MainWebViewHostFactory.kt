// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.soai.android.databinding.ActivityMainBinding
import com.soai.android.web.WebViewHost

internal object MainWebViewHostFactory {
    fun create(
        activity: AppCompatActivity,
        binding: ActivityMainBinding,
        configure: (WebViewHost) -> Unit
    ): WebViewHost {
        val webView = WebView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        binding.webViewContainer.removeAllViews()
        binding.webViewContainer.addView(webView)
        return WebViewHost(
            webView = webView,
            progressBar = binding.progressBar,
            errorLayout = binding.errorLayout,
            errorText = binding.errorText,
            retryButton = binding.retryButton
        ).also(configure)
    }
}
