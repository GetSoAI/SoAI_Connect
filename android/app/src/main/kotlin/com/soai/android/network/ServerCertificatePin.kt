// SPDX-License-Identifier: MIT

package com.soai.android.network

import android.annotation.SuppressLint
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object ServerCertificatePin {

    fun isApplicable(
        serverOrigin: String?,
        serverInstanceId: String?,
        pinnedOrigin: String?,
        pinnedInstanceId: String?
    ): Boolean {
        return !serverOrigin.isNullOrBlank() &&
            !serverInstanceId.isNullOrBlank() &&
            serverOrigin == pinnedOrigin &&
            serverInstanceId == pinnedInstanceId
    }

    fun normalizeFingerprint(fingerprint: String): String {
        return fingerprint.replace(":", "").replace(" ", "").uppercase()
    }

    fun isValidFingerprint(fingerprint: String?): Boolean {
        if (fingerprint.isNullOrBlank()) return false
        val normalized = normalizeFingerprint(fingerprint)
        return normalized.length == SHA_256_HEX_LENGTH && normalized.all { character ->
            character.isDigit() || character in 'A'..'F'
        }
    }

    fun sha256Hex(certificate: X509Certificate): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return bytes.joinToString("") { byte -> "%02X".format(byte) }
    }

    fun matches(certificate: X509Certificate, pinnedFingerprint: String): Boolean {
        return matchesFingerprint(sha256Hex(certificate), pinnedFingerprint)
    }

    fun matchesFingerprint(fingerprint: String?, pinnedFingerprint: String?): Boolean {
        if (!isValidFingerprint(fingerprint) || !isValidFingerprint(pinnedFingerprint)) return false
        val actual = normalizeFingerprint(fingerprint.orEmpty()).toByteArray(Charsets.US_ASCII)
        val expected = normalizeFingerprint(pinnedFingerprint.orEmpty()).toByteArray(Charsets.US_ASCII)
        return MessageDigest.isEqual(actual, expected)
    }

    private const val SHA_256_HEX_LENGTH = 64
}

@SuppressLint("CustomX509TrustManager")
class PinnedFingerprintTrustManager(pinnedFingerprint: String) : X509TrustManager {

    private val normalizedPin = ServerCertificatePin.normalizeFingerprint(pinnedFingerprint).also {
        require(ServerCertificatePin.isValidFingerprint(it)) { "Invalid SHA-256 certificate fingerprint" }
    }
    private val systemTrustManager = platformDefaultTrustManager()

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw SSLPeerUnverifiedException("Client certificates are not supported")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull()
            ?: throw SSLPeerUnverifiedException("Missing server certificate")
        if (ServerCertificatePin.matchesFingerprint(ServerCertificatePin.sha256Hex(leaf), normalizedPin)) {
            return
        }
        systemTrustManager.checkServerTrusted(chain, authType)
    }

    fun matchesFingerprint(certificate: X509Certificate): Boolean {
        return ServerCertificatePin.matchesFingerprint(ServerCertificatePin.sha256Hex(certificate), normalizedPin)
    }

    private fun platformDefaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
