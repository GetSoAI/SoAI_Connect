// SPDX-License-Identifier: MIT

package com.soai.android.network

internal enum class QrConnectDecision {
    AUTO_CONNECT,
    REVIEW,
    INSTANCE_MISMATCH
}

internal object QrAutoConnectPolicy {
    fun decide(payload: ConnectPairingPayload, result: DiscoveryResult): QrConnectDecision {
        return when {
            result.instanceId != payload.instanceId -> QrConnectDecision.INSTANCE_MISMATCH
            result.scheme != payload.scheme ||
                result.port != payload.port ||
                result.fallbackActive ||
                result.cleartextToPublicHost -> QrConnectDecision.REVIEW
            else -> QrConnectDecision.AUTO_CONNECT
        }
    }
}
