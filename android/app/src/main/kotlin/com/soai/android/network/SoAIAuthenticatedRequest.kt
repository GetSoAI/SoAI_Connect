// SPDX-License-Identifier: MIT

package com.soai.android.network

import com.soai.android.web.SoAISessionCookies
import okhttp3.Request

object SoAIAuthenticatedRequest {
    private const val CSRF_HEADER_NAME = "X-SoAI-CSRF"

    fun applyHeaders(
        builder: Request.Builder,
        serverUrl: String,
        cookieHeader: String?
    ): Request.Builder {
        val origin = requireNotNull(ServerOrigin.normalize(serverUrl)) {
            "Configured server URL has no valid HTTP origin"
        }
        builder.header("Origin", origin)
        cookieHeader?.takeIf { it.isNotBlank() }?.let { cookie ->
            builder.header("Cookie", cookie)
            SoAISessionCookies.csrfToken(serverUrl, cookie)?.let { token ->
                builder.header(CSRF_HEADER_NAME, token)
            }
        }
        return builder
    }
}
