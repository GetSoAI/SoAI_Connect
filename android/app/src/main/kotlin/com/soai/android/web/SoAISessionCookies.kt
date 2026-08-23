// SPDX-License-Identifier: MIT

package com.soai.android.web

import java.net.URI

internal object SoAISessionCookies {
    fun hasAuthentication(serverUrl: String, cookieHeader: String?): Boolean {
        return cookieValue(cookieHeader, names(serverUrl).auth) != null
    }

    fun csrfToken(serverUrl: String, cookieHeader: String?): String? {
        return cookieValue(cookieHeader, names(serverUrl).csrf)
    }

    private fun cookieValue(cookieHeader: String?, name: String): String? {
        if (cookieHeader.isNullOrBlank()) return null
        return cookieHeader
            .split(';')
            .asSequence()
            .map { part -> part.trim() }
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val cookieName = part.substring(0, separator).trim()
                if (cookieName != name) return@mapNotNull null
                part.substring(separator + 1).trim().ifBlank { null }
            }
            .firstOrNull()
    }

    private fun names(serverUrl: String): CookieNames {
        return when (URI(serverUrl).scheme?.lowercase()) {
            "http" -> HTTP_COOKIE_NAMES
            "https" -> HTTPS_COOKIE_NAMES
            else -> error("Configured server URL has no supported WebUI cookie transport")
        }
    }

    private data class CookieNames(val auth: String, val csrf: String)

    private val HTTP_COOKIE_NAMES = CookieNames("soai-http-token", "soai-http-csrf")
    private val HTTPS_COOKIE_NAMES = CookieNames("__Host-soai-token", "__Host-soai-csrf")
}
