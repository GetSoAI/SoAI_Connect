// Unauthenticated TLS inspection before page loading for network/ServerCertificateInspection.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal suspend fun unapprovedServerCertificate(
    serverUrl: String,
    verifiedClient: OkHttpClient = HttpClientFactory.create().newBuilder()
        .callTimeout(CERTIFICATE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
    inspectionClientFactory: ((X509Certificate) -> Unit) -> OkHttpClient = HttpClientFactory::createDiscoveryTlsReader
): X509Certificate? {
    try {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url("${serverUrl.trimEnd('/')}/api/v1/system/health")
                .header("Accept", "application/json").get().build()
            try {
                executeHttpCall(verifiedClient, request) { }
                return@withContext null
            } catch (exception: IOException) {
                if (!isTlsVerificationFailure(exception)) throw exception
            }
            val certificate = AtomicReference<X509Certificate>()
            val inspectionClient = inspectionClientFactory(certificate::set).newBuilder()
                .callTimeout(CERTIFICATE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
            try {
                executeHttpCall(inspectionClient, request) {
                    certificate.get() ?: throw SSLPeerUnverifiedException("Server certificate is unavailable")
                }
            } finally {
                HttpClientRelease.release(inspectionClient)
            }
        }
    } finally {
        HttpClientRelease.release(verifiedClient)
    }
}

private const val CERTIFICATE_CALL_TIMEOUT_SECONDS = 20L
