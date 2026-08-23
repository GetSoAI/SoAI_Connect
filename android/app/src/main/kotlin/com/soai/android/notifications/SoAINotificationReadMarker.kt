// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.soai.android.data.AppPreferences
import com.soai.android.network.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

object SoAINotificationReadMarker {
    private const val TAG = "SoAINotificationRead"

    suspend fun markRead(context: Context, notificationId: String) {
        val appContext = context.applicationContext
        val prefs = AppPreferences.getInstance(appContext)
        val serverUrl = prefs.serverUrl ?: return
        SoAINotificationPresenter.cancel(appContext, notificationId)
        withContext(Dispatchers.IO) {
            val httpClient = HttpClientFactory.createPinnedRest(prefs)
            try {
                val client = SoAINotificationApiClient(
                    httpClient,
                    CookieManager.getInstance()
                )
                if (!client.markRead(serverUrl, notificationId)) {
                    Log.w(TAG, "Backend rejected notification mark-read request")
                }
                Unit
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Failed to mark notification read", exception)
            } finally {
                httpClient.dispatcher.cancelAll()
                httpClient.dispatcher.executorService.shutdown()
                httpClient.connectionPool.evictAll()
            }
        }
    }
}
