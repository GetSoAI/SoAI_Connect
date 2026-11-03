// Typed account-action outcomes for network/SessionActionResult.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.network

import com.soai.android.web.WebPageFailure
import com.soai.android.R
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

sealed interface SessionActionResult {
    data object Success : SessionActionResult
    data class Failure(val messageResource: Int, val traceId: String? = null, val code: String? = null) : SessionActionResult
}

internal suspend fun executeSessionAction(client: OkHttpClient, request: Request): SessionActionResult {
    return try {
        executeHttpCall(client, request) { response ->
            if (response.isSuccessful) SessionActionResult.Success
            else {
                val body = readBoundedBody(response, 64L * 1024L)
                val error = try {
                    body?.let { JSONObject(it).optJSONObject("error") }
                } catch (_: JSONException) {
                    null
                }
                val traceId = error?.optString("trace_id")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
                val code = error?.optString("code")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
                SessionActionResult.Failure(sessionFailureResource(response.code), traceId, code)
            }
        }
    } catch (exception: IOException) {
        SessionActionResult.Failure(WebPageFailure.networkMessageResource(exception))
    }
}

internal fun sessionFailureResource(statusCode: Int): Int {
    return when (statusCode) {
        400, 409, 422 -> R.string.session_action_rejected
        404 -> R.string.session_action_missing
        401, 403, 429, in 500..599 -> WebPageFailure.httpMessageResource(statusCode)
        else -> R.string.session_action_failed
    }
}
