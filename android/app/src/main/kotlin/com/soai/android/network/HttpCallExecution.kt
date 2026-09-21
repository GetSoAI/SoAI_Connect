// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal suspend fun executeHttpCall(client: OkHttpClient, request: Request): Response {
    return suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, responseToClose, _ -> responseToClose.close() }
            }
        })
    }
}

internal suspend fun executeHttpCallForSuccess(client: OkHttpClient, request: Request): Boolean {
    return withContext(Dispatchers.IO) {
        executeHttpCall(client, request).use { response -> response.isSuccessful }
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
