// SPDX-License-Identifier: MIT

package com.soai.android.network

import android.net.Uri
import java.net.URI

object ServerOrigin {

    fun normalize(url: String): String? {
        val uri = try {
            URI(url)
        } catch (exception: Exception) {
            return null
        }
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return normalize(scheme, host, uri.port)
    }

    fun normalize(uri: Uri): String? {
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return normalize(scheme, host, uri.port)
    }

    internal fun normalize(scheme: String, host: String, requestedPort: Int): String? {
        val normalizedScheme = scheme.lowercase()
        val normalizedHost = host.lowercase().removeSurrounding("[", "]")
        if (normalizedHost.isBlank()) return null
        val port = normalizePort(normalizedScheme, requestedPort)
        if (port == -1) return null
        val formattedHost = if (normalizedHost.contains(":")) "[$normalizedHost]" else normalizedHost
        return "$normalizedScheme://$formattedHost:$port"
    }

    fun isSameOrigin(a: Uri, b: Uri): Boolean {
        val originA = normalize(a) ?: return false
        val originB = normalize(b) ?: return false
        return originA == originB
    }

    private fun normalizePort(scheme: String, port: Int): Int {
        if (port != -1) return port.takeIf { it in 1..65535 } ?: -1
        return when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
    }
}
