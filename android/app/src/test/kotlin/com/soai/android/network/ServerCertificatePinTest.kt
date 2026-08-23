// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCertificatePinTest {

    private val certificatePem = """
        -----BEGIN CERTIFICATE-----
        MIIDFzCCAf+gAwIBAgIUbR2Cu3ih4uczfcSVdI8cft4owPMwDQYJKoZIhvcNAQEL
        BQAwGjEYMBYGA1UEAwwPc29haS10ZXN0LmxvY2FsMCAXDTI2MDcxMDEzMDUzN1oY
        DzIxMjYwNjE2MTMwNTM3WjAaMRgwFgYDVQQDDA9zb2FpLXRlc3QubG9jYWwwggEi
        MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDIjLzNb3bIT+VhLX9jJYkFhBKm
        zIuHa1aCiMv94B6bb6zacgK4DxzWGcS2LsXO1iJyzYaKKr+DTUqPiSAEP7uEZsJK
        JGzqFRXVDawvEfZ9NhJCRs09T7SP9iKFMFNrsi4uBHoGQlaQrkg5p3XFzwXJV5qF
        xviGqOjxUOwCHIt0aiFa9CXu240yEuqoP99IrAhhfDqaxdjhmQiYPC6GrNbizdfz
        RPXTIQGlQ3PFd94FkzmHZ4QWU1SwevIFlZGsh+bVQDPGbWPkYqogW+8QVzaCRUn4
        Do4y72aN75tERQ42Gh0lABF0AzL1UsCpXgEsMImNKjHcthpkVfgwA8wI+0h/AgMB
        AAGjUzBRMB0GA1UdDgQWBBSwWHKGM2SqtuipQtV5FJVAqtza+jAfBgNVHSMEGDAW
        gBSwWHKGM2SqtuipQtV5FJVAqtza+jAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3
        DQEBCwUAA4IBAQB7ERhysajrLKk4RIovIotnpyW6PyqAZgofTRu98y+FQmaoHHwz
        6j0/uFhqx6FV8qgOmy5BnUN05DycHgqLy8Bid0Pf1n3ILQ//HleBjKZK4q+SKL+S
        DZVXl/DR9y+tedOUMl5ow228VZFj6adTYkjYFTygUyD85DvS7aISu3ah6JncE0Op
        cBxEtk8y42yhPykS7DycmiHwILw80KaUpCDTzIZQkTp4JJU0OPsdYuGvG4P/4USA
        lp8jk3Viie0iJesVoyKjHXIi/MK0u77snJ0ZnZrgZFVKO9jw1/j1zuNHoNzIitsU
        8T3L4iocHl03P+4zvfwhjgOxDjiffzlV2No7
        -----END CERTIFICATE-----
    """.trimIndent()

    private val certificateSha256 =
        "75:EA:50:E3:3D:C1:C9:0F:6D:75:C1:85:9A:FA:19:4F:C9:47:1C:31:F9:73:53:1E:CC:2C:07:1A:2F:48:41:94"

    private fun loadCertificate(): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(
            ByteArrayInputStream(certificatePem.toByteArray(Charsets.US_ASCII))
        ) as X509Certificate
    }

    @Test
    fun applicablePin_requiresExactOriginAndInstanceIdentity() {
        assertTrue(ServerCertificatePin.isApplicable(
            "https://soai.local:443",
            "instance-1",
            "https://soai.local:443",
            "instance-1"
        ))
        assertFalse(ServerCertificatePin.isApplicable(
            "https://other.local:443",
            "instance-1",
            "https://soai.local:443",
            "instance-1"
        ))
        assertFalse(ServerCertificatePin.isApplicable(
            "https://soai.local:443",
            "instance-2",
            "https://soai.local:443",
            "instance-1"
        ))
        assertFalse(ServerCertificatePin.isApplicable(
            "https://soai.local:443",
            null,
            "https://soai.local:443",
            null
        ))
    }

    @Test
    fun normalizeFingerprint_stripsSeparatorsAndUppercases() {
        val normalized = ServerCertificatePin.normalizeFingerprint("ab:cd ef")
        assertEquals("ABCDEF", normalized)
    }

    @Test
    fun sha256Hex_matchesKnownFingerprint() {
        val hex = ServerCertificatePin.sha256Hex(loadCertificate())
        assertEquals(ServerCertificatePin.normalizeFingerprint(certificateSha256), hex)
    }

    @Test
    fun matches_acceptsColonSeparatedAndLowercasePins() {
        val certificate = loadCertificate()
        assertTrue(ServerCertificatePin.matches(certificate, certificateSha256))
        assertTrue(ServerCertificatePin.matches(certificate, certificateSha256.lowercase()))
        assertTrue(
            ServerCertificatePin.matches(certificate, certificateSha256.replace(":", ""))
        )
    }

    @Test
    fun matches_rejectsDifferentFingerprint() {
        val wrongPin = "00".repeat(32)
        assertFalse(ServerCertificatePin.matches(loadCertificate(), wrongPin))
    }

    @Test
    fun matches_rejectsMalformedFingerprints() {
        assertFalse(ServerCertificatePin.matchesFingerprint("ABCD", "ABCD"))
        assertFalse(ServerCertificatePin.matchesFingerprint("GG".repeat(32), "GG".repeat(32)))
    }

    @Test
    fun trustManager_acceptsPinnedLeafCertificate() {
        val trustManager = PinnedFingerprintTrustManager(certificateSha256)
        trustManager.checkServerTrusted(arrayOf(loadCertificate()), "RSA")
        assertTrue(trustManager.matchesFingerprint(loadCertificate()))
    }

    @Test
    fun trustManager_rejectsMismatchedUntrustedLeafCertificate() {
        val trustManager = PinnedFingerprintTrustManager("11".repeat(32))
        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(arrayOf(loadCertificate()), "RSA")
        }
        assertFalse(trustManager.matchesFingerprint(loadCertificate()))
    }

    @Test
    fun trustManager_rejectsEmptyChainAndClientAuth() {
        val trustManager = PinnedFingerprintTrustManager(certificateSha256)
        assertThrows(SSLPeerUnverifiedException::class.java) {
            trustManager.checkServerTrusted(emptyArray(), "RSA")
        }
        assertThrows(SSLPeerUnverifiedException::class.java) {
            trustManager.checkClientTrusted(arrayOf(loadCertificate()), "RSA")
        }
    }
}
