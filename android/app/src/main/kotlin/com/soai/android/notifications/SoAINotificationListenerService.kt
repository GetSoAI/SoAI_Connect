// Notification service and reconnect supervision for notifications/SoAINotificationListenerService.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.app.Service
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soai.android.SoAIApplication
import com.soai.android.data.AppPreferences
import com.soai.android.network.HttpClientFactory
import com.soai.android.network.HttpClientRelease
import com.soai.android.web.SoAISessionCookies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SoAINotificationListenerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val networkChanges = Channel<Unit>(Channel.CONFLATED)
    private lateinit var prefs: AppPreferences
    private lateinit var seenStore: SoAINotificationSeenStore
    private var listenerJob: Job? = null
    private var activeServerUrl: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences.getInstance(this)
        seenStore = SoAINotificationSeenStore(prefs)
        SoAINotificationChannels.ensure(this)
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        foregroundStarted = try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                SoAINotificationPresenter.buildForegroundNotification(this, NotificationConnectionStatus.CONNECTING),
                foregroundServiceType
            )
            true
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Android rejected the notification listener foreground start", exception)
            false
        }
        if (foregroundStarted) observeNetworkChanges()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverUrl = prefs.serverUrl
        if (!foregroundStarted || !listenerAllowed(serverUrl)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (activeServerUrl == serverUrl && listenerJob?.isActive == true) return START_STICKY
        listenerJob?.cancel()
        val listenerServerUrl = serverUrl ?: return START_NOT_STICKY
        activeServerUrl = listenerServerUrl
        listenerJob = serviceScope.launch {
            try {
                var outcome: SessionConnectionOutcome?
                do {
                    outcome = runListener(listenerServerUrl)
                } while (isActive && outcome != SessionConnectionOutcome.AUTHENTICATION_REVOKED &&
                    outcome != SessionConnectionOutcome.FAILED && listenerAllowed(listenerServerUrl))
                if (isActive) stopSelf(startId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Notification listener stopped after an unexpected failure", exception)
                if (isActive) stopSelf(startId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        foregroundStarted = false
        serviceScope.cancel()
        networkCallback?.let { callback ->
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
        }
        networkCallback = null
        networkChanges.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeNetworkChanges() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkChanges.trySend(Unit)
            }

            override fun onLost(network: Network) {
                networkChanges.trySend(Unit)
            }
        }
        try {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Network-change observation unavailable; timed reconnection remains active", exception)
        }
    }

    private suspend fun runListener(serverUrl: String): SessionConnectionOutcome? {
        var backoffMillis = ReconnectBackoff.INITIAL_MILLIS
        var lastOutcome: SessionConnectionOutcome? = null
        val webSocketClient = HttpClientFactory.createPinnedWebSocket(prefs)
        val restClient = HttpClientFactory.createPinnedRest(prefs)
        val apiClient = SoAINotificationApiClient(restClient, CookieManager.getInstance()::getCookie)
        try {
            while (currentCoroutineContext().isActive && listenerAllowed(serverUrl)) {
                while (networkChanges.tryReceive().isSuccess) Unit
                val connection = SoAINotificationConnection(
                    serverUrl,
                    apiClient,
                    seenStore,
                    onRecords = { records ->
                        if (listenerAllowed(serverUrl) && !SoAIApplication.instance.isAppForegrounded()) {
                            records.forEach { record ->
                                SoAINotificationPresenter.show(this, SoAINotificationTextResolver.resolveDisplay(this, record))
                            }
                        }
                    },
                    onCancelled = { ids -> SoAINotificationPresenter.cancelKnown(this, ids) },
                    onStatus = ::showConnectionStatus
                )
                showConnectionStatus(NotificationConnectionStatus.CONNECTING)
                val outcome = connection.run(webSocketClient, networkChanges) { listenerAllowed(serverUrl) }
                lastOutcome = outcome
                if (outcome == SessionConnectionOutcome.AUTHENTICATION_REVOKED || outcome == SessionConnectionOutcome.FAILED) break
                if (!currentCoroutineContext().isActive || !listenerAllowed(serverUrl)) break
                if (outcome == SessionConnectionOutcome.NETWORK_CHANGED) continue
                backoffMillis = ReconnectBackoff.next(backoffMillis, connection.connectionLifetimeMillis)
                withTimeoutOrNull(ReconnectBackoff.jittered(backoffMillis)) { networkChanges.receive() }
            }
        } finally {
            HttpClientRelease.release(restClient, webSocketClient)
        }
        return lastOutcome
    }

    private fun showConnectionStatus(status: NotificationConnectionStatus) {
        if (!foregroundStarted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(
            FOREGROUND_NOTIFICATION_ID,
            SoAINotificationPresenter.buildForegroundNotification(this, status)
        )
    }

    private fun listenerAllowed(serverUrl: String?): Boolean {
        if (!prefs.androidNotificationsEnabled || prefs.incognitoMode || !prefs.hasConnectedServer()) return false
        if (serverUrl.isNullOrBlank() || prefs.serverUrl != serverUrl) return false
        if (!SoAINotificationPresenter.canRunListener(this)) return false
        return SoAISessionCookies.hasAuthentication(serverUrl, CookieManager.getInstance().getCookie(serverUrl))
    }

    companion object {
        private const val TAG = "SoAINotificationSvc"
        private const val FOREGROUND_NOTIFICATION_ID = 4601
    }
}
