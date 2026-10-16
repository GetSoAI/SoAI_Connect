// Lifecycle-bound server certificate approval and identity checks for ui/ServerTrustController.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.network.CertificateSummary
import com.soai.android.network.CertificateUtils
import com.soai.android.network.HttpClientFactory
import com.soai.android.network.ServerCertificatePin
import com.soai.android.network.ServerOrigin
import com.soai.android.network.SessionCertificateTrust
import com.soai.android.network.unapprovedServerCertificate
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.WebPageFailure
import com.soai.android.web.WebViewHost
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class ServerTrustController(
    private val activity: AppCompatActivity,
    private val prefs: AppPreferences
) {
    private data class PendingTrust(
        val host: WebViewHost,
        val origin: String,
        val instanceId: String,
        val summary: CertificateSummary,
        val approve: () -> Unit,
        val cancel: () -> Unit
    )

    private val pending = ArrayDeque<PendingTrust>()
    private val certificateRejectedHosts = mutableSetOf<WebView>()
    private var dialog: AlertDialog? = null
    private var hostResumed = false
    private var destroyed = false

    fun resetSessionTrust() {
        SessionCertificateTrust.clear()
        certificateRejectedHosts.clear()
        cancelAllPending()
    }

    fun onHostResumed() {
        hostResumed = true
        showNextPrompt()
    }

    fun onHostPaused() {
        hostResumed = false
    }

    suspend fun preparePageLoad(host: WebViewHost): Boolean {
        certificateRejectedHosts.remove(host.webView)
        val serverUrl = prefs.serverUrl
        val instanceId = prefs.serverInstanceId
        val origin = serverUrl?.let(ServerOrigin::normalize)
        if (serverUrl == null || instanceId.isNullOrBlank() || origin == null) {
            host.showError(activity.getString(R.string.error_invalid_address))
            return false
        }
        if (serverUrl.toUri().scheme != "https") return true
        val summary = try {
            unapprovedServerCertificate(serverUrl, HttpClientFactory.createPinnedRest(prefs))?.let(CertificateUtils::summarize)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            host.showError(activity.getString(WebPageFailure.networkMessageResource(exception)))
            return false
        } catch (exception: Exception) {
            Log.e(TAG, "Page certificate inspection failed", exception)
            host.showError(activity.getString(R.string.ssl_error_unavailable_certificate))
            return false
        }
        if (!ServerCertificatePin.isApplicable(
                prefs.serverUrl?.let(ServerOrigin::normalize), prefs.serverInstanceId, origin, instanceId
            ) || destroyed || activity.isFinishing || activity.isDestroyed
        ) return false
        if (summary == null || ServerCertificatePin.matchesFingerprint(summary.sha256Hex, effectiveFingerprint(origin))) return true
        return suspendCancellableCoroutine { continuation ->
            pending.addLast(PendingTrust(host, origin, instanceId, summary,
                approve = { if (continuation.isActive) continuation.resume(true) },
                cancel = { if (continuation.isActive) continuation.resume(false) }))
            continuation.invokeOnCancellation { activity.runOnUiThread { cancelFor(host.webView) } }
            showNextPrompt()
        }
    }

    private fun effectiveFingerprint(origin: String): String? {
        return SessionCertificateTrust.effectiveFingerprint(origin, prefs.serverInstanceId,
            prefs.trustedServerOrigin, prefs.trustedServerInstanceId, prefs.trustedServerCertSha256)
    }

    fun handleSslError(host: WebViewHost, handler: SslErrorHandler, error: SslError) {
        if (destroyed || activity.isFinishing || activity.isDestroyed) {
            handler.cancel()
            return
        }
        val configuredUrl = prefs.serverUrl
        if (configuredUrl == null || ServerOrigin.normalize(configuredUrl) != ServerOrigin.normalizeTlsEndpoint(error.url)) {
            handler.cancel()
            host.showError(activity.getString(R.string.ssl_error_wrong_origin))
            return
        }
        val origin = ServerOrigin.normalize(configuredUrl)
        val instanceId = prefs.serverInstanceId
        val summary = CertificateUtils.summarize(error.certificate)
        if (origin == null || instanceId.isNullOrBlank() || summary == null) {
            handler.cancel()
            host.showError(activity.getString(R.string.ssl_error_unavailable_certificate))
            return
        }
        if (ServerCertificatePin.matchesFingerprint(summary.sha256Hex, effectiveFingerprint(origin))) {
            handler.proceed()
            return
        }
        if (host.webView in certificateRejectedHosts) {
            handler.cancel()
            return
        }
        pending.addLast(PendingTrust(host, origin, instanceId, summary, handler::proceed, handler::cancel))
        showNextPrompt()
    }

    fun cancelFor(webView: WebView) {
        certificateRejectedHosts.remove(webView)
        val displayedRequestRemoved = pending.firstOrNull()?.host?.webView === webView
        val removed = pending.filter { request -> request.host.webView === webView }
        if (removed.isEmpty()) return

        pending.removeAll(removed.toSet())
        removed.forEach { request -> request.cancel() }
        if (displayedRequestRemoved) {
            dialog?.setOnCancelListener(null)
            dialog?.setOnDismissListener(null)
            dialog?.dismiss()
            dialog = null
            showNextPrompt()
        }
    }

    fun destroy() {
        destroyed = true
        certificateRejectedHosts.clear()
        cancelAllPending()
    }

    private fun cancelAllPending() {
        dialog?.setOnCancelListener(null)
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        while (pending.isNotEmpty()) pending.removeFirst().cancel()
    }

    private fun showNextPrompt() {
        if (dialog != null || pending.isEmpty() || destroyed) return
        if (activity.isFinishing || activity.isDestroyed) {
            cancelAllPending()
            return
        }
        if (!hostResumed) return
        val request = pending.first()
        val hasStoredPin = ServerCertificatePin.isApplicable(
            request.origin,
            prefs.serverInstanceId,
            prefs.trustedServerOrigin,
            prefs.trustedServerInstanceId
        ) && ServerCertificatePin.isValidFingerprint(prefs.trustedServerCertSha256)
        val details = activity.getString(
            R.string.certificate_details,
            request.origin,
            request.summary.subjectName,
            request.summary.issuerName,
            CertificateUtils.formatHexFingerprint(request.summary.sha256Hex)
        )
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (hasStoredPin) R.string.certificate_changed_title else R.string.untrusted_certificate_title)
            .setMessage(details)
            .setPositiveButton(R.string.trust_once) { _, _ -> acceptMatching(request, false) }
            .setNeutralButton(R.string.trust_always) { _, _ -> acceptMatching(request, true) }
            .setNegativeButton(R.string.cancel) { _, _ -> rejectMatching(request) }
            .setOnCancelListener { rejectMatching(request) }
            .setOnDismissListener {
                dialog = null
                showNextPrompt()
            }
            .show()
    }

    private fun acceptMatching(request: PendingTrust, persist: Boolean) {
        if (!ServerCertificatePin.isApplicable(
                prefs.serverUrl?.let(ServerOrigin::normalize), prefs.serverInstanceId,
                request.origin, request.instanceId
            )
        ) {
            rejectMatching(request)
            return
        }
        if (persist) {
            prefs.saveTrustedServerCertificate(request.origin, request.instanceId, request.summary.sha256Hex)
            SessionCertificateTrust.clear()
        } else {
            SessionCertificateTrust.remember(request.origin, request.instanceId, request.summary.sha256Hex)
        }
        SoAINotificationServiceController.restart(activity)
        removeMatching(request).forEach { it.approve() }
    }

    private fun rejectMatching(request: PendingTrust) {
        removeMatching(request).forEach {
            certificateRejectedHosts.add(it.host.webView)
            it.cancel()
            it.host.showError(activity.getString(R.string.ssl_error_blocked))
        }
    }

    private fun removeMatching(request: PendingTrust): List<PendingTrust> {
        val matching = pending.filter {
            it.origin == request.origin && it.instanceId == request.instanceId && ServerCertificatePin.matchesFingerprint(
                it.summary.sha256Hex,
                request.summary.sha256Hex
            )
        }
        pending.removeAll(matching.toSet())
        return matching
    }

    companion object {
        private const val TAG = "ServerTrustController"
    }
}
