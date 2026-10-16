// Authenticated notification requests and typed catch-up outcomes for notifications/SoAINotificationApiClient.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import com.soai.android.network.SoAIAuthenticatedRequest
import com.soai.android.network.executeHttpCall
import com.soai.android.network.isTlsVerificationFailure
import com.soai.android.network.readBoundedBody
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SoAINotificationApiClient(
    private val httpClient: OkHttpClient,
    private val cookieHeader: (String) -> String?
) {
    suspend fun fetchUnread(serverUrl: String): UnreadNotificationResult = withContext(Dispatchers.IO) {
        try {
            executeHttpCall(httpClient, buildUnreadRequest(serverUrl)) { response ->
                if (!response.isSuccessful) {
                    return@executeHttpCall UnreadNotificationResult.Failed(
                        when (response.code) {
                            401 -> NotificationCatchUpFailure.AUTHENTICATION_REQUIRED
                            403 -> NotificationCatchUpFailure.ACCESS_DENIED
                            408, 429, in 500..599 -> NotificationCatchUpFailure.TRANSIENT
                            else -> NotificationCatchUpFailure.INVALID_RESPONSE
                        },
                        retryAfterMillis(response.header("Retry-After"))
                    )
                }
                val body = readBoundedBody(response, MAX_UNREAD_BODY_BYTES)
                    ?: return@executeHttpCall UnreadNotificationResult.Failed(NotificationCatchUpFailure.INVALID_RESPONSE)
                UnreadNotificationResult.Fetched(SoAINotificationParser.parseListResponse(body))
            }
        } catch (exception: IOException) {
            UnreadNotificationResult.Failed(
                if (isTlsVerificationFailure(exception)) NotificationCatchUpFailure.CERTIFICATE_APPROVAL_REQUIRED
                else NotificationCatchUpFailure.TRANSIENT
            )
        } catch (exception: JSONException) {
            UnreadNotificationResult.Failed(NotificationCatchUpFailure.INVALID_RESPONSE)
        }
    }

    suspend fun markRead(serverUrl: String, notificationId: String): Boolean = withContext(Dispatchers.IO) {
        if (notificationId.isBlank()) return@withContext false
        val payload = JSONObject()
            .put("notification_ids", JSONArray().put(notificationId))
            .toString()
            .toRequestBody(JSON)
        executeHttpCall(httpClient,
            Request.Builder()
                .url("${serverUrl.trimEnd('/')}/api/v1/webui/notifications/mark-read")
                .applySoAIHeaders(serverUrl)
                .post(payload)
                .build()
        ) { response ->
            response.isSuccessful
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

    suspend fun probeAuthentication(serverUrl: String): Boolean? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/v1/webui/users/me")
            .applySoAIHeaders(serverUrl)
            .get()
            .build()
        executeHttpCall(httpClient, request) { response ->
            if (response.isSuccessful) return@executeHttpCall true
            if (response.code == 401 || response.code == 403) return@executeHttpCall false
            null
        }
    }

    private fun Request.Builder.applySoAIHeaders(serverUrl: String): Request.Builder {
        return SoAIAuthenticatedRequest.applyHeaders(this, serverUrl, cookieHeader(serverUrl))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_UNREAD_BODY_BYTES = 2L * 1024L * 1024L
    }
}

internal fun retryAfterMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    val normalized = value?.trim() ?: return null
    normalized.toLongOrNull()?.let { seconds ->
        if (seconds < 0) return null
        return seconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
    }
    return try {
        (ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant().toEpochMilli() - nowMillis).coerceAtLeast(0L)
    } catch (exception: DateTimeParseException) {
        null
    }
}

sealed interface UnreadNotificationResult {
    data class Fetched(val records: List<SoAINotificationRecord>) : UnreadNotificationResult
    data class Failed(
        val failure: NotificationCatchUpFailure,
        val retryAfterMillis: Long? = null
    ) : UnreadNotificationResult
}

enum class NotificationCatchUpFailure {
    TRANSIENT,
    AUTHENTICATION_REQUIRED,
    ACCESS_DENIED,
    CERTIFICATE_APPROVAL_REQUIRED,
    INVALID_RESPONSE
}
