// Typed backend health and endpoint verification for network/BackendHealthVerifier.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.network

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

internal const val SOAI_PRODUCT_MARKER = "soai"

internal data class HealthCheck(
    val endpoint: RuntimeEndpointPayload?,
    val tlsStatus: TlsStatus,
    val rejectedNonSoAI: Boolean,
    val transientSoAIFailure: Boolean
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
            executeHttpCall(httpClient, request) { response -> buildHealthCheck(response, verifiedTlsStatus) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (scheme == "https" && isTlsVerificationFailure(e)) {
                return verifyHealthIgnoringCertificate(request)
            }
            unresolvedHealthCheck()
        }
    }

    private suspend fun verifyHealthIgnoringCertificate(request: Request): HealthCheck {
        return try {
            executeHttpCall(tlsReaderClient, request) { response -> buildHealthCheck(response, TlsStatus.UNTRUSTED) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unresolvedHealthCheck()
        }
    }

    private fun buildHealthCheck(response: Response, tlsStatus: TlsStatus): HealthCheck {
        if (!response.isSuccessful) {
            val transientSoAIFailure = response.code == 503 && isTypedTransientSoAIResponse(
                readBoundedBody(response, MAX_HEALTH_BODY_BYTES)
            )
            return HealthCheck(
                endpoint = null,
                tlsStatus = TlsStatus.NONE,
                rejectedNonSoAI = !transientSoAIFailure,
                transientSoAIFailure = transientSoAIFailure
            )
        }
        val endpoint = DiscoveryPayloadParser.parse(
            readBoundedBody(response, MAX_HEALTH_BODY_BYTES)
        ) ?: return HealthCheck(
            endpoint = null,
            tlsStatus = TlsStatus.NONE,
            rejectedNonSoAI = true,
            transientSoAIFailure = false
        )
        return HealthCheck(
            endpoint = endpoint,
            tlsStatus = tlsStatus,
            rejectedNonSoAI = false,
            transientSoAIFailure = false
        )
    }

    private fun unresolvedHealthCheck(): HealthCheck {
        return HealthCheck(
            endpoint = null,
            tlsStatus = TlsStatus.NONE,
            rejectedNonSoAI = false,
            transientSoAIFailure = false
        )
    }

    private fun isTypedTransientSoAIResponse(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val errorCode = try {
            JSONObject(body).optJSONObject("error")?.opt("code") as? String
        } catch (_: Exception) {
            null
        }
        return errorCode in TRANSIENT_SOAI_ERROR_CODES
    }

    companion object {
        private const val MAX_HEALTH_BODY_BYTES = 64L * 1024L
        private val TRANSIENT_SOAI_ERROR_CODES = setOf("server_not_ready", "server_shutting_down")
    }
}
