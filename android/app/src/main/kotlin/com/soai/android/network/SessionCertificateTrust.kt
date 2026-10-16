// Origin-bound temporary certificate trust and effective trust selection for network/SessionCertificateTrust.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.network

object SessionCertificateTrust {

    private var trustedOrigin: String? = null
    private var trustedInstanceId: String? = null
    private var trustedFingerprint: String? = null

    @Synchronized
    fun remember(origin: String, instanceId: String, fingerprint: String) {
        if (!ServerCertificatePin.isValidFingerprint(fingerprint)) return
        if (origin.isBlank() || instanceId.isBlank()) return
        trustedOrigin = origin
        trustedInstanceId = instanceId
        trustedFingerprint = ServerCertificatePin.normalizeFingerprint(fingerprint)
    }

    @Synchronized
    fun clear() {
        trustedOrigin = null
        trustedInstanceId = null
        trustedFingerprint = null
    }

    @Synchronized
    fun fingerprintFor(serverOrigin: String?, serverInstanceId: String?): String? {
        val fingerprint = trustedFingerprint ?: return null
        if (!ServerCertificatePin.isApplicable(
                serverOrigin,
                serverInstanceId,
                trustedOrigin,
                trustedInstanceId
            )
        ) {
            return null
        }
        return fingerprint
    }

    @Synchronized
    fun effectiveFingerprint(
        serverOrigin: String?,
        serverInstanceId: String?,
        storedOrigin: String?,
        storedInstanceId: String?,
        storedFingerprint: String?
    ): String? {
        fingerprintFor(serverOrigin, serverInstanceId)?.let { return it }
        if (!ServerCertificatePin.isApplicable(serverOrigin, serverInstanceId, storedOrigin, storedInstanceId)) {
            return null
        }
        return storedFingerprint?.takeIf(ServerCertificatePin::isValidFingerprint)
    }
}
