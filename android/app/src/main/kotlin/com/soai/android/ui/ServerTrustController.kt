// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.net.http.SslError
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
import com.soai.android.network.ServerCertificatePin
import com.soai.android.network.ServerOrigin
import com.soai.android.network.SessionCertificateTrust
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.WebViewHost

internal class ServerTrustController(
    private val activity: AppCompatActivity,
    private val prefs: AppPreferences
) {
    private data class PendingTrust(
        val host: WebViewHost,
        val handler: SslErrorHandler,
        val origin: String,
        val summary: CertificateSummary
    )

    private val pending = ArrayDeque<PendingTrust>()
    private var dialog: AlertDialog? = null
    private var trustedOnceOrigin: String? = null
    private var trustedOnceFingerprint: String? = null
    private var hostResumed = false
    private var destroyed = false

    fun resetSessionTrust() {
        trustedOnceOrigin = null
        trustedOnceFingerprint = null
        SessionCertificateTrust.clear()
        cancelAllPending()
    }

    fun onHostResumed() {
        hostResumed = true
        showNextPrompt()
    }

    fun onHostPaused() {
        hostResumed = false
    }

    fun handleSslError(host: WebViewHost, handler: SslErrorHandler, error: SslError) {
        if (destroyed || activity.isFinishing || activity.isDestroyed) {
            handler.cancel()
            return
        }
        val configuredUrl = prefs.serverUrl
        if (configuredUrl == null || !ServerOrigin.isSameOrigin(configuredUrl.toUri(), error.url.toUri())) {
            handler.cancel()
            host.showError(activity.getString(R.string.ssl_error_wrong_origin))
            return
        }
        val origin = ServerOrigin.normalize(configuredUrl)
        val summary = CertificateUtils.summarize(error.certificate)
        if (origin == null || summary == null) {
            handler.cancel()
            host.showError(activity.getString(R.string.ssl_error_unavailable_certificate))
            return
        }
        if (isTrustedStored(origin, summary.sha256Hex) || isTrustedOnce(origin, summary.sha256Hex)) {
            handler.proceed()
            return
        }
        pending.addLast(PendingTrust(host, handler, origin, summary))
        showNextPrompt()
    }

    fun cancelFor(webView: WebView) {
        val displayedRequestRemoved = pending.firstOrNull()?.host?.webView === webView
        val removed = pending.filter { request -> request.host.webView === webView }
        if (removed.isEmpty()) return

        pending.removeAll(removed.toSet())
        removed.forEach { request -> request.handler.cancel() }
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
        cancelAllPending()
    }

    private fun cancelAllPending() {
        dialog?.setOnCancelListener(null)
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        while (pending.isNotEmpty()) pending.removeFirst().handler.cancel()
    }

    private fun isTrustedStored(origin: String, fingerprint: String): Boolean {
        return ServerCertificatePin.isApplicable(
            origin,
            prefs.serverInstanceId,
            prefs.trustedServerOrigin,
            prefs.trustedServerInstanceId
        ) && ServerCertificatePin.matchesFingerprint(
            fingerprint,
            prefs.trustedServerCertSha256
        )
    }

    private fun isTrustedOnce(origin: String, fingerprint: String): Boolean {
        return trustedOnceOrigin == origin &&
            ServerCertificatePin.matchesFingerprint(fingerprint, trustedOnceFingerprint)
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
            request.summary.subjectCn,
            request.summary.issuerCn,
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
        trustedOnceOrigin = request.origin
        trustedOnceFingerprint = request.summary.sha256Hex
        prefs.serverInstanceId?.let { instanceId ->
            if (persist) {
                prefs.saveTrustedServerCertificate(
                    request.origin,
                    instanceId,
                    request.summary.sha256Hex
                )
            } else {
                SessionCertificateTrust.remember(
                    request.origin,
                    instanceId,
                    request.summary.sha256Hex
                )
            }
            SoAINotificationServiceController.restart(activity)
        }
        removeMatching(request).forEach { it.handler.proceed() }
    }

    private fun rejectMatching(request: PendingTrust) {
        removeMatching(request).forEach {
            it.handler.cancel()
            it.host.showError(activity.getString(R.string.ssl_error_blocked))
        }
    }

    private fun removeMatching(request: PendingTrust): List<PendingTrust> {
        val matching = pending.filter {
            it.origin == request.origin && ServerCertificatePin.matchesFingerprint(
                it.summary.sha256Hex,
                request.summary.sha256Hex
            )
        }
        pending.removeAll(matching.toSet())
        return matching
    }
}
