// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.webkit.CookieManager
import android.util.Log
import com.soai.android.network.SoAIAuthenticatedRequest
import com.soai.android.network.readBoundedBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SoAINotificationApiClient(
    private val httpClient: OkHttpClient,
    private val cookieManager: CookieManager
) {
    fun fetchUnread(serverUrl: String): List<SoAINotificationRecord>? {
        val response = httpClient.newCall(buildUnreadRequest(serverUrl)).execute()
        response.use {
            if (!it.isSuccessful) return null
            val body = readBoundedBody(it, MAX_UNREAD_BODY_BYTES) ?: return null
            return try {
                SoAINotificationParser.parseListResponse(body)
            } catch (exception: Exception) {
                Log.w(TAG, "Ignoring malformed unread notification response", exception)
                null
            }
        }
    }

    fun markRead(serverUrl: String, notificationId: String): Boolean {
        if (notificationId.isBlank()) return false
        val payload = JSONObject()
            .put("notification_ids", JSONArray().put(notificationId))
            .toString()
            .toRequestBody(JSON)
        httpClient.newCall(
            Request.Builder()
                .url("${serverUrl.trimEnd('/')}/api/v1/webui/notifications/mark-read")
                .applySoAIHeaders(serverUrl)
                .post(payload)
                .build()
        ).execute().use { response ->
            return response.isSuccessful
        }
    }

    fun buildWebSocketRequest(serverUrl: String): Request {
        return Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/v1/system/ws")
            .applySoAIHeaders(serverUrl)
            .build()
    }

    private fun buildUnreadRequest(serverUrl: String): Request {
        return Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/v1/webui/notifications?limit=500&unread_only=true")
            .applySoAIHeaders(serverUrl)
            .get()
            .build()
    }

    fun probeAuthentication(serverUrl: String): Boolean? {
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/v1/webui/users/me")
            .applySoAIHeaders(serverUrl)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) return true
            if (response.code == 401 || response.code == 403) return false
            return null
        }
    }

    private fun Request.Builder.applySoAIHeaders(serverUrl: String): Request.Builder {
        return SoAIAuthenticatedRequest.applyHeaders(this, serverUrl, cookieManager.getCookie(serverUrl))
    }

    companion object {
        private const val TAG = "SoAINotificationApi"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_UNREAD_BODY_BYTES = 2L * 1024L * 1024L
    }
}
