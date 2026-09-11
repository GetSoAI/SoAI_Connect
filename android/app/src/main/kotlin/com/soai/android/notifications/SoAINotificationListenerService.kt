// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.ServiceCompat
import com.soai.android.SoAIApplication
import com.soai.android.data.AppPreferences
import com.soai.android.network.HttpClientFactory
import com.soai.android.web.SoAISessionCookies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class SoAINotificationListenerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: AppPreferences
    private lateinit var seenStore: SoAINotificationSeenStore

    @Volatile
    private var listenerJob: Job? = null

    @Volatile
    private var activeCoordinator: SoAINotificationStreamCoordinator? = null

    @Volatile
    private var activeServerUrl: String? = null

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
        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            SoAINotificationPresenter.buildForegroundNotification(this),
            foregroundServiceType
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverUrl = prefs.serverUrl
        if (!listenerAllowed(serverUrl)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (activeServerUrl == serverUrl && listenerJob?.isActive == true) {
            return START_STICKY
        }
        listenerJob?.cancel()
        val listenerServerUrl = serverUrl ?: return START_NOT_STICKY
        activeServerUrl = listenerServerUrl
        listenerJob = serviceScope.launch {
            try {
                var outcome: ListenerRunOutcome
                do {
                    outcome = runListener(listenerServerUrl)
                } while (
                    isActive &&
                    outcome == ListenerRunOutcome.POLICY_DISALLOWED &&
                    listenerAllowed(listenerServerUrl)
                )
                if (isActive) stopSelf()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Notification listener stopped after an unexpected failure", exception)
                if (isActive) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        listenerJob?.cancel()
        activeCoordinator = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runListener(serverUrl: String): ListenerRunOutcome {
        var backoffMillis = ReconnectBackoff.INITIAL_MILLIS
        var listenerOutcome = ListenerRunOutcome.POLICY_DISALLOWED
        val webSocketClient = HttpClientFactory.createPinnedWebSocket(prefs)
        val restClient = HttpClientFactory.createPinnedRest(prefs)
        val apiClient = SoAINotificationApiClient(restClient, CookieManager.getInstance())
        try {
            while (serviceScope.isActive && listenerAllowed(serverUrl)) {
                val coordinator = SoAINotificationStreamCoordinator(seenStore)
                val finished = CompletableDeferred<SessionConnectionOutcome>()
                val openedAtMillis = AtomicLong(0L)
                val connectionJob = SupervisorJob(currentCoroutineContext()[Job])
                val connectionScope = CoroutineScope(currentCoroutineContext() + connectionJob)
                activeCoordinator = coordinator
                val webSocket = webSocketClient.newWebSocket(
                    apiClient.buildWebSocketRequest(serverUrl),
                    createWebSocketListener(serverUrl, apiClient, coordinator, finished, connectionScope) {
                        openedAtMillis.set(SystemClock.elapsedRealtime())
                    }
                )
                try {
                    var outcome: SessionConnectionOutcome? = null
                    while (outcome == null && listenerAllowed(serverUrl)) {
                        outcome = withTimeoutOrNull(LISTENER_POLICY_CHECK_INTERVAL_MILLIS) {
                            finished.await()
                        }
                    }
                    if (outcome == SessionConnectionOutcome.AUTHENTICATION_REVOKED) {
                        listenerOutcome = ListenerRunOutcome.AUTHENTICATION_REVOKED
                        break
                    }
                } finally {
                    if (activeCoordinator === coordinator) {
                        activeCoordinator = null
                    }
                    webSocket.cancel()
                    connectionJob.cancelAndJoin()
                }
                if (!serviceScope.isActive || !listenerAllowed(serverUrl)) {
                    break
                }
                val openedAt = openedAtMillis.get()
                val connectionLifetime = if (openedAt == 0L) 0L else SystemClock.elapsedRealtime() - openedAt
                backoffMillis = ReconnectBackoff.next(backoffMillis, connectionLifetime)
                delay(ReconnectBackoff.jittered(backoffMillis))
            }
        } finally {
            restClient.dispatcher.cancelAll()
            webSocketClient.dispatcher.cancelAll()
            restClient.connectionPool.evictAll()
            webSocketClient.connectionPool.evictAll()
            restClient.dispatcher.executorService.shutdown()
            webSocketClient.dispatcher.executorService.shutdown()
        }
        return listenerOutcome
    }

    private fun createWebSocketListener(
        serverUrl: String,
        apiClient: SoAINotificationApiClient,
        coordinator: SoAINotificationStreamCoordinator,
        finished: CompletableDeferred<SessionConnectionOutcome>,
        connectionScope: CoroutineScope,
        onOpened: () -> Unit
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpened()
                if (activeCoordinator !== coordinator) {
                    webSocket.cancel()
                    finished.complete(SessionConnectionOutcome.RECONNECT)
                    return
                }
                connectionScope.launch {
                    try {
                        syncUnreadNotifications(serverUrl, apiClient, coordinator)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.w(TAG, "Initial notification sync failed", exception)
                    } finally {
                        postAll(coordinator, coordinator.completeInitialSync())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(webSocket, text, coordinator)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val outcome = if (SessionClosePolicy.isTerminalAuthenticationClose(code)) {
                    SessionConnectionOutcome.AUTHENTICATION_REVOKED
                } else {
                    SessionConnectionOutcome.RECONNECT
                }
                finished.complete(outcome)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Notification WebSocket failed", t)
                if (response?.code == 401 || response?.code == 403) {
                    finished.complete(SessionConnectionOutcome.AUTHENTICATION_REVOKED)
                    return
                }
                connectionScope.launch {
                    val authenticated = try {
                        apiClient.probeAuthentication(serverUrl)
                    } catch (exception: Exception) {
                        Log.w(TAG, "Session probe after WebSocket failure failed", exception)
                        null
                    }
                    val outcome = if (authenticated == false) {
                        SessionConnectionOutcome.AUTHENTICATION_REVOKED
                    } else {
                        SessionConnectionOutcome.RECONNECT
                    }
                    finished.complete(outcome)
                }
            }
        }
    }

    private fun handleWebSocketMessage(
        webSocket: WebSocket,
        text: String,
        coordinator: SoAINotificationStreamCoordinator
    ) {
        if (activeCoordinator !== coordinator) return
        val payload = try {
            JSONObject(text)
        } catch (exception: Exception) {
            Log.w(TAG, "Ignoring malformed WebSocket notification payload", exception)
            return
        }
        when (payload.optionalString("type")) {
            "ping" -> webSocket.send("""{"type":"pong"}""")
            "NotificationCreatedEvent" -> {
                val record = SoAINotificationParser.parseCreatedEvent(payload) ?: return
                postAll(coordinator, coordinator.onCreated(record))
            }
            "NotificationsMarkedReadEvent" -> {
                cancelAll(coordinator.suppress(SoAINotificationParser.parseMarkedReadIds(payload)))
            }
            "NotificationDeletedEvent" -> {
                payload.optionalString("notification_id")?.let { id ->
                    cancelAll(coordinator.suppress(listOf(id)))
                }
            }
            "NotificationsClearedEvent" -> {
                cancelAll(coordinator.clearAll(SoAINotificationParser.parseEventTimestampMs(payload)))
            }
        }
    }

    private fun syncUnreadNotifications(
        serverUrl: String,
        apiClient: SoAINotificationApiClient,
        coordinator: SoAINotificationStreamCoordinator
    ) {
        val records = apiClient.fetchUnread(serverUrl) ?: return
        if (activeCoordinator !== coordinator) return
        val baseline = seenStore.shouldBaseline(serverUrl)
        val toPost = coordinator.onUnreadFetched(records, baseline)
        if (baseline) {
            seenStore.markBaseline(serverUrl)
        }
        postAll(coordinator, toPost)
    }

    private fun postAll(coordinator: SoAINotificationStreamCoordinator, records: List<SoAINotificationRecord>) {
        if (records.isEmpty()) return
        if (activeCoordinator !== coordinator) return
        if (SoAIApplication.instance.isAppForegrounded()) return
        records.forEach { record ->
            SoAINotificationPresenter.show(this, SoAINotificationTextResolver.resolveDisplay(this, record))
        }
    }

    private fun cancelAll(notificationIds: Collection<String>) {
        SoAINotificationPresenter.cancelKnown(this, notificationIds)
    }

    private fun listenerAllowed(serverUrl: String?): Boolean {
        if (!prefs.androidNotificationsEnabled || prefs.incognitoMode || !prefs.hasConnectedServer()) return false
        if (serverUrl.isNullOrBlank()) return false
        if (!SoAINotificationPresenter.canRunListener(this)) return false
        val cookieHeader = CookieManager.getInstance().getCookie(serverUrl)
        return SoAISessionCookies.hasAuthentication(serverUrl, cookieHeader)
    }

    companion object {
        private const val TAG = "SoAINotificationSvc"
        private const val FOREGROUND_NOTIFICATION_ID = 4601
        private const val LISTENER_POLICY_CHECK_INTERVAL_MILLIS = 30_000L
    }
}

private enum class ListenerRunOutcome {
    AUTHENTICATION_REVOKED,
    POLICY_DISALLOWED
}
