// SPDX-License-Identifier: MIT

package com.soai.android.network

import android.annotation.SuppressLint
import com.soai.android.data.AppPreferences
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object HttpClientFactory {

    fun create(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun createDiscoveryTlsReader(): OkHttpClient {
        val trustManager = DiscoveryReaderTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun createPinnedRest(prefs: AppPreferences): OkHttpClient {
        return createPinnedBuilder(prefs)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(REST_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun createPinnedWebSocket(prefs: AppPreferences): OkHttpClient {
        return createPinnedBuilder(prefs)
            .readTimeout(WEBSOCKET_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(WEBSOCKET_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun createPinnedBuilder(prefs: AppPreferences): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)

        val pinnedFingerprint = resolvePinnedFingerprint(prefs)
        if (pinnedFingerprint != null) {
            val trustManager = PinnedFingerprintTrustManager(pinnedFingerprint)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { hostname, session ->
                val certificate = try {
                    session.peerCertificates.firstOrNull() as? X509Certificate
                } catch (_: SSLPeerUnverifiedException) {
                    null
                }
                when {
                    certificate == null -> false
                    trustManager.matchesFingerprint(certificate) -> true
                    else -> HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                }
            }
        }
        return builder
    }

    private fun resolvePinnedFingerprint(prefs: AppPreferences): String? {
        val serverInstanceId = prefs.serverInstanceId
        val serverOrigin = prefs.serverUrl?.let { ServerOrigin.normalize(it) }
        val storedFingerprint = prefs.trustedServerCertSha256
            ?.takeIf { fingerprint -> ServerCertificatePin.isValidFingerprint(fingerprint) }
        if (
            storedFingerprint != null &&
            ServerCertificatePin.isApplicable(
                serverOrigin,
                serverInstanceId,
                prefs.trustedServerOrigin,
                prefs.trustedServerInstanceId
            )
        ) {
            return storedFingerprint
        }
        return SessionCertificateTrust.fingerprintFor(serverOrigin, serverInstanceId)
    }

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 10L
    private const val WRITE_TIMEOUT_SECONDS = 10L
    private const val REST_CALL_TIMEOUT_SECONDS = 20L
    private const val WEBSOCKET_PING_INTERVAL_SECONDS = 30L
    private const val WEBSOCKET_HANDSHAKE_TIMEOUT_SECONDS = 20L
}

@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private class DiscoveryReaderTrustManager : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw SSLPeerUnverifiedException("Client certificates are not supported")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
