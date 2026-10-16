// Cancellable HTTP response consumption and bounded network reads for network/HttpCallExecution.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal suspend fun <ResponseValue> executeHttpCall(
    client: OkHttpClient,
    request: Request,
    consumeResponse: (Response) -> ResponseValue
): ResponseValue {
    return suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, failure: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(failure)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    val result = response.use(consumeResponse)
                    continuation.resume(result)
                } catch (exception: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(exception)
                }
            }
        })
    }
}

internal fun isTlsVerificationFailure(exception: Throwable): Boolean {
    var current: Throwable? = exception
    while (current != null) {
        if (current is SSLHandshakeException || current is SSLPeerUnverifiedException) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun readBoundedBody(response: Response, maximumBytes: Long): String? {
    val body = response.body ?: return null
    val contentLength = body.contentLength()
    if (contentLength > maximumBytes) return null
    val source = body.source()
    source.request(maximumBytes + 1L)
    if (source.buffer.size > maximumBytes) return null
    return source.buffer.clone().readUtf8()
}
