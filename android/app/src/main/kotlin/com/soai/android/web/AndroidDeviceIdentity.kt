// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.webkit.CookieManager
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

data class AndroidDeviceIdentity(
    val deviceId: String,
    val deviceLabel: String
) {
    init {
        require(UUID.fromString(deviceId).toString() == deviceId.lowercase())
        require(deviceLabel == normalizeLabel(deviceLabel))
        require(deviceLabel.isNotEmpty())
    }

    companion object {
        const val MAX_LABEL_LENGTH = 80

        fun normalizeLabel(value: String): String {
            val trimmed = value.trim()
            if (trimmed.codePointCount(0, trimmed.length) <= MAX_LABEL_LENGTH) return trimmed
            return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_LABEL_LENGTH))
        }
    }
}

object AndroidDeviceIdentityCookie {
    private const val COOKIE_NAME = "soai-android-device"
    private const val LOGIN_PATH = "/api/v1/webui/auth/login"

    fun build(serverUrl: String, identity: AndroidDeviceIdentity): String {
        val scheme = URI(serverUrl).scheme?.lowercase()
        require(scheme == "http" || scheme == "https")
        val payload = JSONObject()
            .put("device_id", identity.deviceId)
            .put("device_label", identity.deviceLabel)
            .toString()
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        val secure = if (scheme == "https") "; Secure" else ""
        return "$COOKIE_NAME=$encoded; Path=$LOGIN_PATH; HttpOnly; SameSite=Strict$secure"
    }

    fun install(
        cookieManager: CookieManager,
        serverUrl: String,
        identity: AndroidDeviceIdentity,
        onComplete: (Boolean) -> Unit
    ) {
        cookieManager.setCookie(serverUrl, build(serverUrl, identity), onComplete)
    }
}
