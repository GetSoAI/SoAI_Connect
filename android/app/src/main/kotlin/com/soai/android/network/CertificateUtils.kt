// SPDX-License-Identifier: MIT

package com.soai.android.network

import android.net.http.SslCertificate
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal data class CertificateSummary(
    val subjectCn: String,
    val issuerCn: String,
    val sha256Hex: String
)

internal object CertificateUtils {

    fun summarize(sslCertificate: SslCertificate?): CertificateSummary? {
        val source = sslCertificate ?: return null
        val cert = extractX509Certificate(source) ?: return null
        return try {
            CertificateSummary(
                subjectCn = source.issuedTo?.cName?.takeIf { it.isNotBlank() }
                    ?: cert.subjectX500Principal.name,
                issuerCn = source.issuedBy?.cName?.takeIf { it.isNotBlank() }
                    ?: cert.issuerX500Principal.name,
                sha256Hex = ServerCertificatePin.sha256Hex(cert)
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to summarize server certificate", exception)
            null
        }
    }

    fun formatHexFingerprint(hex: String): String {
        return ServerCertificatePin.normalizeFingerprint(hex).chunked(2).joinToString(":")
    }

    private fun extractX509Certificate(sslCertificate: SslCertificate): X509Certificate? {
        val bundle = SslCertificate.saveState(sslCertificate) ?: return null
        val bytes = bundle.getByteArray("x509-certificate") ?: return null
        val certFactory = CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
    }

    private const val TAG = "CertificateUtils"
}
