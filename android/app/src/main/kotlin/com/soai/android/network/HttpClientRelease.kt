// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.util.concurrent.Executor
import okhttp3.OkHttpClient

object HttpClientRelease {
    private const val THREAD_NAME = "SoAIHttpClientRelease"

    private val backgroundExecutor = Executor { task -> Thread(task, THREAD_NAME).start() }

    fun release(vararg clients: OkHttpClient) {
        releaseOn(backgroundExecutor, clients.toList())
    }

    internal fun releaseOn(executor: Executor, clients: List<OkHttpClient>) {
        executor.execute {
            clients.forEach { client ->
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }
}
