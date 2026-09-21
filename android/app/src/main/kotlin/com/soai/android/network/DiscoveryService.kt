// SPDX-License-Identifier: MIT

package com.soai.android.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoveryResult(
    val serverUrl: String,
    val port: Int,
    val scheme: String,
    val tlsStatus: TlsStatus,
    val instanceId: String,
    val instanceName: String?,
    val version: String,
    val preferredPort: Int,
    val fallbackActive: Boolean,
    val cleartextToPublicHost: Boolean
)

enum class TlsStatus {
    NONE,
    TRUSTED,
    UNTRUSTED
}

class DiscoveryService(
    private val httpClient: OkHttpClient,
    private val discoveryTlsReaderClient: OkHttpClient,
    private val discoveryPorts: List<Int> = DISCOVERY_PORTS
) {

    private val healthVerifier = BackendHealthVerifier(httpClient, discoveryTlsReaderClient)

    fun close() {
        HttpClientRelease.release(httpClient, discoveryTlsReaderClient)
    }

    suspend fun discover(
        input: String,
        targetInstanceId: String? = null
    ): DiscoveryResult = withContext(Dispatchers.IO) {
        if (DiscoveryInputParser.hasIpv6ZoneIdentifier(input)) {
            throw DiscoveryException(DiscoveryFailureReason.IPV6_ZONE_UNSUPPORTED)
        }
        val parsed = DiscoveryInputParser.parse(input)
        if (parsed.hostname.isBlank()) {
            throw DiscoveryException(DiscoveryFailureReason.INVALID_ADDRESS)
        }
        val explicitPort = parsed.explicitPort
        if (explicitPort != null && explicitPort !in 1..65535) {
            throw DiscoveryException(DiscoveryFailureReason.INVALID_PORT, explicitPort.toString())
        }

        if (explicitPort != null) {
            return@withContext probeBackendWithSchemes(parsed.hostname, explicitPort, parsed.preferredScheme)
        }

        return@withContext discoverFromPorts(
            parsed.hostname,
            parsed.preferredScheme,
            targetInstanceId
        )
    }

    private suspend fun discoverFromPorts(
        hostname: String,
        preferredScheme: String?,
        targetInstanceId: String?
    ): DiscoveryResult = supervisorScope {
        val deferreds = discoveryPorts.map { port ->
            async {
                try {
                    probeDiscoveryPort(hostname, port, preferredScheme)
                } catch (e: CancellationException) {
                    throw e
                } catch (exception: Exception) {
                    Log.w(TAG, "Discovery port $port failed for $hostname", exception)
                    null
                }
            }
        }
        val portProbes = deferreds.mapNotNull { it.await() }
        val candidates = portProbes.mapNotNull { it.result }
        when (val selection = DiscoveryEndpointSelector.select(candidates, targetInstanceId)) {
            is DiscoverySelection.Selected -> selection.endpoint
            is DiscoverySelection.Ambiguous -> throw DiscoveryException(
                DiscoveryFailureReason.AMBIGUOUS,
                selection.endpoints.joinToString(", ") { it.serverUrl }
            )
            DiscoverySelection.NotFound -> {
                val matchingTransientEndpoint = portProbes.any { probe ->
                    probe.transientInstanceId != null &&
                        (targetInstanceId.isNullOrBlank() || probe.transientInstanceId == targetInstanceId)
                }
                val reason = if (matchingTransientEndpoint) {
                    DiscoveryFailureReason.TEMPORARILY_UNAVAILABLE
                } else {
                    DiscoveryFailureReason.NOT_FOUND
                }
                throw DiscoveryException(reason, hostname)
            }
        }
    }

    private data class DiscoveryPortProbe(
        val result: DiscoveryResult?,
        val transientInstanceId: String?
    )

    private suspend fun probeDiscoveryPort(
        hostname: String,
        port: Int,
        preferredScheme: String?
    ): DiscoveryPortProbe {
        var transientInstanceId: String? = null
        for (scheme in buildPreferredSchemeList(preferredScheme)) {
            val probe = probeDiscoveryPayload(hostname, port, scheme)
            if (probe.result != null) return probe
            transientInstanceId = transientInstanceId ?: probe.transientInstanceId
        }
        return DiscoveryPortProbe(result = null, transientInstanceId = transientInstanceId)
    }

    private suspend fun probeDiscoveryPayload(
        hostname: String,
        port: Int,
        scheme: String
    ): DiscoveryPortProbe {
        val payload = withTimeoutOrNull(SCHEME_PHASE_TIMEOUT) {
            fetchDiscoveryPayload(hostname, port, scheme)
        } ?: return DiscoveryPortProbe(result = null, transientInstanceId = null)
        val probe = withTimeoutOrNull(SCHEME_PHASE_TIMEOUT) {
            probeBackend(hostname, payload.port, payload.scheme)
        } ?: return DiscoveryPortProbe(result = null, transientInstanceId = null)
        val result = probe.result?.takeIf { candidate ->
            candidate.instanceId == payload.instanceId &&
                candidate.scheme == payload.scheme &&
                candidate.port == payload.port &&
                candidate.preferredPort == payload.preferredPort &&
                candidate.fallbackActive == payload.fallbackActive
        }
        return DiscoveryPortProbe(
            result = result,
            transientInstanceId = payload.instanceId.takeIf {
                result == null && probe.transientSoAIFailure
            }
        )
    }

    private suspend fun fetchDiscoveryPayload(
        hostname: String,
        port: Int,
        scheme: String
    ): RuntimeEndpointPayload? {
        val host = DiscoveryInputParser.formatHost(hostname)
        val discoveryUrl = "$scheme://$host:$port/"
        val request = Request.Builder().url(discoveryUrl).get().build()
        val body = readDiscoveryBody(request, scheme) ?: return null
        return DiscoveryPayloadParser.parse(body)
    }

    private suspend fun readDiscoveryBody(request: Request, scheme: String): String? {
        try {
            return executeHttpCall(httpClient, request).use { resp ->
                if (!resp.isSuccessful) null else readBoundedBody(resp, MAX_DISCOVERY_BODY_BYTES)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (scheme == "https" && isTlsVerificationFailure(e)) {
                return readDiscoveryBodyIgnoringCertificate(request)
            }
            return null
        }
    }

    private suspend fun readDiscoveryBodyIgnoringCertificate(request: Request): String? {
        return try {
            executeHttpCall(discoveryTlsReaderClient, request).use { resp ->
                if (!resp.isSuccessful) null else readBoundedBody(resp, MAX_DISCOVERY_BODY_BYTES)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun probeBackendWithSchemes(hostname: String, port: Int, preferredScheme: String?): DiscoveryResult {
        val endpointResponseSchemes = mutableListOf<String>()
        var transientSoAIFailure = false
        for (scheme in buildPreferredSchemeList(preferredScheme)) {
            val probe = withTimeoutOrNull(SCHEME_PHASE_TIMEOUT) {
                probeBackend(hostname, port, scheme)
            } ?: continue
            if (probe.result != null) {
                return probe.result
            }
            if (probe.rejectedNonSoAI || probe.transientSoAIFailure) {
                endpointResponseSchemes.add(scheme)
            }
            transientSoAIFailure = transientSoAIFailure || probe.transientSoAIFailure
        }

        if (endpointResponseSchemes.isNotEmpty()) {
            for (scheme in endpointResponseSchemes) {
                probeDiscoveryPayload(hostname, port, scheme).result?.let { result -> return result }
            }
        }
        if (transientSoAIFailure) {
            throw DiscoveryException(DiscoveryFailureReason.TEMPORARILY_UNAVAILABLE, "$hostname:$port")
        }
        if (endpointResponseSchemes.isNotEmpty()) {
            throw DiscoveryException(DiscoveryFailureReason.NOT_SOAI_SERVER, "$hostname:$port")
        }
        throw DiscoveryException(DiscoveryFailureReason.NOT_REACHABLE, "$hostname:$port")
    }

    private data class BackendProbe(
        val result: DiscoveryResult?,
        val rejectedNonSoAI: Boolean,
        val transientSoAIFailure: Boolean
    )

    private suspend fun probeBackend(
        hostname: String,
        port: Int,
        scheme: String
    ): BackendProbe {
        val host = DiscoveryInputParser.formatHost(hostname)
        val healthUrl = "$scheme://$host:$port/api/v1/system/health"

        val health = healthVerifier.verifyHealth(healthUrl, scheme = scheme)
        val endpoint = health.endpoint
            ?: return BackendProbe(
                result = null,
                rejectedNonSoAI = health.rejectedNonSoAI,
                transientSoAIFailure = health.transientSoAIFailure
            )
        if (endpoint.scheme != scheme || endpoint.port != port) {
            return BackendProbe(
                result = null,
                rejectedNonSoAI = true,
                transientSoAIFailure = false
            )
        }

        return BackendProbe(
            result = DiscoveryResult(
                serverUrl = "$scheme://$host:$port",
                port = port,
                scheme = scheme,
                tlsStatus = health.tlsStatus,
                instanceId = endpoint.instanceId,
                instanceName = endpoint.instanceName,
                version = endpoint.version,
                preferredPort = endpoint.preferredPort,
                fallbackActive = endpoint.fallbackActive,
                cleartextToPublicHost = scheme == "http" &&
                    !CleartextPolicy.allowsCleartext(hostname)
            ),
            rejectedNonSoAI = false,
            transientSoAIFailure = false
        )
    }

    internal fun buildPreferredSchemeList(preferredScheme: String?): List<String> {
        val schemes = listOf("http", "https")
        val preferred = preferredScheme?.lowercase()?.takeIf { it in schemes } ?: return schemes
        return if (preferred == "http") listOf("http", "https") else listOf("https", "http")
    }

    companion object {
        private const val TAG = "DiscoveryService"
        private val DISCOVERY_PORTS = listOf(7950, 7951, 7952, 7953, 7954, 7955, 7956, 7957, 7958, 7959, 7960)
        private const val SCHEME_PHASE_TIMEOUT = 6_000L
        private const val MAX_DISCOVERY_BODY_BYTES = 64L * 1024L
    }
}
