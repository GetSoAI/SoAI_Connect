// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.content.Context
import androidx.annotation.ColorRes
import com.soai.android.R
import com.soai.android.network.DiscoveryException
import com.soai.android.network.DiscoveryFailureReason
import com.soai.android.network.DiscoveryResult
import com.soai.android.network.TlsStatus

internal data class DiscoveryStatus(val text: String, @param:ColorRes val colorRes: Int)

internal object DiscoveryResultPresentation {

    fun describeFailure(context: Context, exception: DiscoveryException): String {
        val detail = exception.detail.orEmpty()
        return when (exception.reason) {
            DiscoveryFailureReason.INVALID_ADDRESS -> context.getString(R.string.error_invalid_address)
            DiscoveryFailureReason.IPV6_ZONE_UNSUPPORTED ->
                context.getString(R.string.error_ipv6_zone_unsupported)
            DiscoveryFailureReason.INVALID_PORT -> context.getString(R.string.error_invalid_port, detail)
            DiscoveryFailureReason.NOT_REACHABLE ->
                context.getString(R.string.error_server_not_reachable, detail)
            DiscoveryFailureReason.TEMPORARILY_UNAVAILABLE ->
                context.getString(R.string.error_server_temporarily_unavailable, detail)
            DiscoveryFailureReason.NOT_FOUND -> context.getString(R.string.error_server_not_found, detail)
            DiscoveryFailureReason.AMBIGUOUS -> context.getString(R.string.error_multiple_servers, detail)
            DiscoveryFailureReason.NOT_SOAI_SERVER ->
                context.getString(R.string.error_not_soai_server, detail)
        }
    }

    fun describeVerified(context: Context, result: DiscoveryResult): DiscoveryStatus {
        val identityLabel = result.instanceName?.let { name ->
            context.getString(R.string.server_identity_named, result.version, name)
        } ?: context.getString(R.string.server_identity, result.version)
        val statusText = if (result.tlsStatus == TlsStatus.UNTRUSTED) {
            context.getString(R.string.server_found_untrusted_tls_identity, identityLabel, result.serverUrl)
        } else {
            context.getString(R.string.server_found_identity, identityLabel, result.serverUrl)
        }
        val fallbackText = if (result.fallbackActive) {
            context.getString(R.string.server_found_fallback, statusText, result.preferredPort, result.port)
        } else {
            statusText
        }
        val text = if (result.cleartextToPublicHost) {
            context.getString(R.string.server_found_cleartext_warning, fallbackText)
        } else {
            fallbackText
        }
        val colorRes = if (result.fallbackActive || result.cleartextToPublicHost) {
            R.color.amber_500
        } else {
            R.color.green_500
        }
        return DiscoveryStatus(text, colorRes)
    }
}
