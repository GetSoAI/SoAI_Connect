// SPDX-License-Identifier: MIT

package com.soai.android.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SoAIAuthenticatedRequestTest {
    @Test
    fun appliesOriginCookieAndCsrfFromOneCanonicalPath() {
        val request = SoAIAuthenticatedRequest.applyHeaders(
            Request.Builder().url("https://soai.local/api/v1/webui/sessions/current"),
            serverUrl = "https://soai.local",
            cookieHeader = "__Host-soai-token=jwt; __Host-soai-csrf=csrf-value"
        ).build()

        assertEquals("https://soai.local:443", request.header("Origin"))
        assertEquals("__Host-soai-token=jwt; __Host-soai-csrf=csrf-value", request.header("Cookie"))
        assertEquals("csrf-value", request.header("X-SoAI-CSRF"))
    }

    @Test
    fun doesNotInventAuthenticationHeadersWithoutCookies() {
        val request = SoAIAuthenticatedRequest.applyHeaders(
            Request.Builder().url("http://127.0.0.1/api/v1/webui/auth/logout"),
            serverUrl = "http://127.0.0.1",
            cookieHeader = null
        ).build()

        assertEquals("http://127.0.0.1:80", request.header("Origin"))
        assertNull(request.header("Cookie"))
        assertNull(request.header("X-SoAI-CSRF"))
    }

    @Test
    fun logoutUsesAuthenticatedPostEndpoint() {
        val request = SoAISessionApi.buildLogoutRequest(
            serverUrl = "https://soai.local/",
            cookieHeader = "__Host-soai-token=jwt; __Host-soai-csrf=csrf"
        )

        assertEquals("POST", request.method)
        assertEquals("/api/v1/webui/auth/logout", request.url.encodedPath)
        assertEquals("jwt", request.header("Cookie")?.substringAfter("__Host-soai-token=")?.substringBefore(';'))
    }

    @Test
    fun rejectsRequestsWhenTheConfiguredServerHasNoCanonicalOrigin() {
        assertThrows(IllegalArgumentException::class.java) {
            SoAIAuthenticatedRequest.applyHeaders(
                Request.Builder().url("https://soai.local/api/v1/webui/users/me"),
                serverUrl = "not a server URL",
                cookieHeader = "soai-token=jwt"
            )
        }
    }
}
