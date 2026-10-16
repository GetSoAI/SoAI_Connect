// Main-frame WebView failure presentation for web/WebPageFailure.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.webkit.WebViewClient
import com.soai.android.R
import com.soai.android.network.isTlsVerificationFailure
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

internal object WebPageFailure {
    fun networkMessageResource(failure: IOException): Int {
        return when {
            failure is UnknownHostException -> R.string.page_error_address
            failure is InterruptedIOException -> R.string.page_error_timeout
            isTlsVerificationFailure(failure) -> R.string.page_error_certificate
            else -> R.string.page_error_connection
        }
    }

    fun messageResource(errorCode: Int?): Int {
        return when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> R.string.page_error_address
            WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO -> R.string.page_error_connection
            WebViewClient.ERROR_TIMEOUT -> R.string.page_error_timeout
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> R.string.page_error_certificate
            WebViewClient.ERROR_AUTHENTICATION -> R.string.page_error_authentication
            WebViewClient.ERROR_UNSUPPORTED_SCHEME, WebViewClient.ERROR_BAD_URL -> R.string.error_invalid_address
            else -> R.string.error_loading_page
        }
    }

    fun httpMessageResource(statusCode: Int): Int {
        return when (statusCode) {
            401 -> R.string.page_error_authentication
            403 -> R.string.page_error_access
            404 -> R.string.page_error_missing
            429 -> R.string.page_error_busy
            in 500..599 -> R.string.page_error_server
            else -> R.string.error_loading_page
        }
    }
}

internal class WebPageLoadState {
    private var failureResource: Int? = null

    fun onStarted(): Boolean = failureResource == null

    fun onFailure(messageResource: Int) {
        failureResource = messageResource
    }

    fun onFinished(): Int? {
        val completedFailure = failureResource
        failureResource = null
        return completedFailure
    }
}
