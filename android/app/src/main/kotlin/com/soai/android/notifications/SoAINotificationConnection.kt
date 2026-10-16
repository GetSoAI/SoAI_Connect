// Main-dispatcher WebSocket lifecycle and unread reconciliation for notifications/SoAINotificationConnection.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.os.SystemClock
import android.util.Log
import com.soai.android.network.isTlsVerificationFailure
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject

internal class SoAINotificationConnection(
    private val serverUrl: String,
    private val apiClient: SoAINotificationApiClient,
    private val seenStore: SoAINotificationSeenStore,
    private val onRecords: (List<SoAINotificationRecord>) -> Unit,
    private val onCancelled: (Collection<String>) -> Unit,
    private val onStatus: (NotificationConnectionStatus) -> Unit,
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime
) {
    private val coordinator = SoAINotificationStreamCoordinator(seenStore)
    private var phase = SocketPhase.HANDSHAKE
    private var malformedPayloadLogged = false

    private var openedAtMillis = 0L
    var connectionLifetimeMillis = 0L
        private set

    suspend fun run(
        webSocketFactory: WebSocket.Factory,
        networkChanges: ReceiveChannel<Unit>,
        listenerAllowed: () -> Boolean
    ): SessionConnectionOutcome? = coroutineScope {
        val finished = CompletableDeferred<SessionConnectionOutcome>()
        val webSocket = webSocketFactory.newWebSocket(
            apiClient.buildWebSocketRequest(serverUrl),
            listener(this, finished)
        )
        try {
            var outcome: SessionConnectionOutcome? = null
            while (isActive && outcome == null && listenerAllowed()) {
                outcome = withTimeoutOrNull(30_000L) {
                    select {
                        finished.onAwait { it }
                        networkChanges.onReceive { SessionConnectionOutcome.NETWORK_CHANGED }
                    }
                }
            }
            outcome
        } finally {
            if (phase == SocketPhase.OPEN) connectionLifetimeMillis = (clockMillis() - openedAtMillis).coerceAtLeast(0L)
            phase = SocketPhase.RELEASED
            webSocket.cancel()
            coroutineContext.cancelChildren()
        }
    }

    private fun listener(
        scope: CoroutineScope,
        finished: CompletableDeferred<SessionConnectionOutcome>
    ): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            scope.launch {
                if (phase != SocketPhase.HANDSHAKE) return@launch
                phase = SocketPhase.OPEN
                openedAtMillis = clockMillis()
                onStatus(NotificationConnectionStatus.CATCHING_UP)
                reconcileUnread(finished)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                if (phase == SocketPhase.OPEN) handleMessage(webSocket, text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch {
                if (!disconnected(finished)) return@launch
                if (SessionClosePolicy.indicatesAuthenticationLoss(code)) {
                    confirmSession(finished)
                } else {
                    finished.complete(SessionConnectionOutcome.RECONNECT)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, failure: Throwable, response: Response?) {
            scope.launch {
                if (!disconnected(finished)) return@launch
                when {
                    isTlsVerificationFailure(failure) -> {
                        onStatus(NotificationConnectionStatus.CERTIFICATE_APPROVAL_REQUIRED)
                        finished.complete(SessionConnectionOutcome.RECONNECT)
                    }
                    failure is IOException -> confirmSession(finished)
                    else -> {
                        Log.e(TAG, "Unexpected notification WebSocket failure", failure)
                        finished.complete(SessionConnectionOutcome.FAILED)
                    }
                }
            }
        }
    }

    private fun disconnected(finished: CompletableDeferred<SessionConnectionOutcome>): Boolean {
        if (phase == SocketPhase.ENDED || phase == SocketPhase.RELEASED || finished.isCompleted) return false
        if (phase == SocketPhase.OPEN) connectionLifetimeMillis = (clockMillis() - openedAtMillis).coerceAtLeast(0L)
        phase = SocketPhase.ENDED
        onStatus(NotificationConnectionStatus.RECONNECTING)
        return true
    }

    private suspend fun confirmSession(finished: CompletableDeferred<SessionConnectionOutcome>) {
        val authenticated = try {
            apiClient.probeAuthentication(serverUrl)
        } catch (exception: IOException) {
            null
        }
        if (authenticated == false) onStatus(NotificationConnectionStatus.SIGN_IN_REQUIRED)
        finished.complete(SessionClosePolicy.outcomeAfterAuthenticationProbe(authenticated))
    }

    private suspend fun reconcileUnread(finished: CompletableDeferred<SessionConnectionOutcome>) {
        var backoffMillis = ReconnectBackoff.INITIAL_MILLIS
        while (true) {
            val result = apiClient.fetchUnread(serverUrl)
            if (phase != SocketPhase.OPEN || finished.isCompleted) return
            when (result) {
                is UnreadNotificationResult.Fetched -> {
                    val baseline = seenStore.shouldBaseline(serverUrl)
                    onRecords(coordinator.onUnreadFetched(result.records, baseline))
                    if (baseline) seenStore.markBaseline(serverUrl)
                    onRecords(coordinator.completeInitialSync())
                    onStatus(NotificationConnectionStatus.CONNECTED)
                    return
                }
                is UnreadNotificationResult.Failed -> {
                    onStatus(when (result.failure) {
                        NotificationCatchUpFailure.TRANSIENT -> NotificationConnectionStatus.CATCHING_UP
                        NotificationCatchUpFailure.AUTHENTICATION_REQUIRED -> NotificationConnectionStatus.SIGN_IN_REQUIRED
                        NotificationCatchUpFailure.ACCESS_DENIED -> NotificationConnectionStatus.ACCESS_DENIED
                        NotificationCatchUpFailure.CERTIFICATE_APPROVAL_REQUIRED -> NotificationConnectionStatus.CERTIFICATE_APPROVAL_REQUIRED
                        NotificationCatchUpFailure.INVALID_RESPONSE -> NotificationConnectionStatus.CATCH_UP_FAILED
                    })
                    if (result.failure == NotificationCatchUpFailure.AUTHENTICATION_REQUIRED) {
                        finished.complete(SessionConnectionOutcome.AUTHENTICATION_REVOKED)
                        return
                    }
                    onRecords(coordinator.completeInitialSync())
                    if (result.failure != NotificationCatchUpFailure.TRANSIENT) return
                    delay(maxOf(ReconnectBackoff.jittered(backoffMillis), result.retryAfterMillis ?: 0L))
                }
            }
            backoffMillis = ReconnectBackoff.next(backoffMillis, 0L)
        }
    }

    private fun handleMessage(webSocket: WebSocket, text: String) {
        val payload = try {
            JSONObject(text)
        } catch (exception: JSONException) {
            if (!malformedPayloadLogged) {
                Log.w(TAG, "Ignoring malformed WebSocket notification payload")
                malformedPayloadLogged = true
            }
            return
        }
        when (payload.optionalString("type")) {
            "ping" -> webSocket.send("""{"type":"pong"}""")
            "NotificationCreatedEvent" -> {
                val record = SoAINotificationParser.parseCreatedEvent(payload) ?: return
                onRecords(coordinator.onCreated(record))
            }
            "NotificationsMarkedReadEvent" -> onCancelled(coordinator.suppress(SoAINotificationParser.parseMarkedReadIds(payload)))
            "NotificationDeletedEvent" -> payload.optionalString("notification_id")?.let { onCancelled(coordinator.suppress(listOf(it))) }
            "NotificationsClearedEvent" -> onCancelled(coordinator.clearAll(SoAINotificationParser.parseEventTimestampMs(payload)))
        }
    }

    private enum class SocketPhase { HANDSHAKE, OPEN, ENDED, RELEASED }

    companion object {
        private const val TAG = "SoAINotificationLink"
    }
}

enum class NotificationConnectionStatus {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    CATCHING_UP,
    CATCH_UP_FAILED,
    CERTIFICATE_APPROVAL_REQUIRED,
    SIGN_IN_REQUIRED,
    ACCESS_DENIED
}
