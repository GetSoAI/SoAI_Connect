// SPDX-License-Identifier: MIT

package com.soai.android.network

import android.webkit.CookieManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SoAISessionApi(
    private val httpClient: OkHttpClient,
    private val cookieManager: CookieManager
) {
    suspend fun logout(serverUrl: String): Boolean {
        val request = buildLogoutRequest(serverUrl, cookieManager.getCookie(serverUrl))
        return executeHttpCall(httpClient, request).use { response -> response.isSuccessful }
    }

    suspend fun renameCurrentDevice(
        serverUrl: String,
        deviceId: String,
        deviceLabel: String
    ): Boolean {
        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("device_label", deviceLabel)
            .toString()
            .toRequestBody(JSON)
        val request = SoAIAuthenticatedRequest.applyHeaders(
            Request.Builder().url("${serverUrl.trimEnd('/')}/api/v1/webui/sessions/current"),
            serverUrl,
            cookieManager.getCookie(serverUrl)
        ).patch(payload).build()
        return executeHttpCall(httpClient, request).use { response -> response.isSuccessful }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        fun buildLogoutRequest(serverUrl: String, cookieHeader: String?): Request {
            return SoAIAuthenticatedRequest.applyHeaders(
                Request.Builder().url("${serverUrl.trimEnd('/')}/api/v1/webui/auth/logout"),
                serverUrl,
                cookieHeader
            ).post(EMPTY_BODY).build()
        }
    }
}
