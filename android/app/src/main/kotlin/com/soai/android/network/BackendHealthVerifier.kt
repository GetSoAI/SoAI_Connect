// SPDX-License-Identifier: MIT

package com.soai.android.network

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal const val SOAI_PRODUCT_MARKER = "soai"

internal data class HealthCheck(
    val endpoint: RuntimeEndpointPayload?,
    val tlsStatus: TlsStatus,
    val rejectedNonSoAI: Boolean
)

internal class BackendHealthVerifier(
    private val httpClient: OkHttpClient,
    private val tlsReaderClient: OkHttpClient
) {

    suspend fun verifyHealth(url: String, scheme: String): HealthCheck {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            val verifiedTlsStatus = if (scheme == "https") TlsStatus.TRUSTED else TlsStatus.NONE
            buildHealthCheck(executeHttpCall(httpClient, request), verifiedTlsStatus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (scheme == "https" && isTlsVerificationFailure(e)) {
                return verifyHealthIgnoringCertificate(request)
            }
            HealthCheck(endpoint = null, tlsStatus = TlsStatus.NONE, rejectedNonSoAI = false)
        }
    }

    private suspend fun verifyHealthIgnoringCertificate(request: Request): HealthCheck {
        return try {
            buildHealthCheck(executeHttpCall(tlsReaderClient, request), TlsStatus.UNTRUSTED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthCheck(endpoint = null, tlsStatus = TlsStatus.NONE, rejectedNonSoAI = false)
        }
    }

    private fun buildHealthCheck(response: Response, tlsStatus: TlsStatus): HealthCheck {
        response.use { resp ->
            if (!resp.isSuccessful) {
                return HealthCheck(endpoint = null, tlsStatus = TlsStatus.NONE, rejectedNonSoAI = false)
            }
            val endpoint = DiscoveryPayloadParser.parse(
                readBoundedBody(resp, MAX_HEALTH_BODY_BYTES)
            ) ?: return HealthCheck(
                endpoint = null,
                tlsStatus = TlsStatus.NONE,
                rejectedNonSoAI = true
            )
            return HealthCheck(endpoint = endpoint, tlsStatus = tlsStatus, rejectedNonSoAI = false)
        }
    }

    companion object {
        private const val MAX_HEALTH_BODY_BYTES = 64L * 1024L
    }
}
