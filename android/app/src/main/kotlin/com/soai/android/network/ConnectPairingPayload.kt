// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.net.URI
import java.net.URISyntaxException

internal data class ConnectPairingPayload(
    val hostname: String,
    val port: Int,
    val scheme: String,
    val instanceId: String
) {
    val discoveryInput: String
        get() = "$scheme://${DiscoveryInputParser.formatHost(hostname)}:$port"

    val pairingUri: String
        get() = "$PAIRING_SCHEME://${DiscoveryInputParser.formatHost(hostname)}:$port?$VERSION_KEY=$SUPPORTED_VERSION&$SCHEME_KEY=$scheme&$INSTANCE_KEY=$instanceId"
}

internal sealed interface PairingScan {
    data class Valid(val payload: ConnectPairingPayload) : PairingScan
    data object Unsupported : PairingScan
    data object Ignored : PairingScan
}

internal object ConnectPairingPayloadParser {
    fun parse(raw: String): PairingScan {
        if (raw.length > MAX_PAYLOAD_LENGTH) return PairingScan.Ignored
        val uri = try {
            URI(raw)
        } catch (_: URISyntaxException) {
            return PairingScan.Ignored
        }
        if (!uri.scheme.equals(PAIRING_SCHEME, ignoreCase = true) || uri.isOpaque) return PairingScan.Ignored
        val query = parseQuery(uri.rawQuery) ?: return PairingScan.Ignored
        val version = query[VERSION_KEY] ?: return PairingScan.Ignored
        if (version != SUPPORTED_VERSION) return PairingScan.Unsupported
        return parseVersionOne(uri, query)?.let { payload -> PairingScan.Valid(payload) } ?: PairingScan.Ignored
    }

    private fun parseVersionOne(uri: URI, query: Map<String, String>): ConnectPairingPayload? {
        if (query.keys != REQUIRED_KEYS) return null
        if (uri.rawUserInfo != null || !uri.rawPath.isNullOrEmpty() || uri.rawFragment != null) return null
        val scheme = query.getValue(SCHEME_KEY).takeIf { value -> value in SERVER_SCHEMES } ?: return null
        val instanceId = query.getValue(INSTANCE_KEY).takeIf { value -> INSTANCE_PATTERN.matches(value) } ?: return null
        val host = uri.host ?: return null
        if (uri.port !in 1..MAX_PORT) return null
        val parsed = DiscoveryInputParser.parse("$host:${uri.port}")
        if (parsed.hostname.isBlank() || parsed.explicitPort != uri.port) return null
        return ConnectPairingPayload(parsed.hostname, uri.port, scheme, instanceId)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String>? {
        if (rawQuery.isNullOrEmpty()) return null
        val entries = rawQuery.split('&').map { parameter ->
            val separator = parameter.indexOf('=')
            if (separator <= 0 || separator == parameter.lastIndex) return null
            parameter.substring(0, separator) to parameter.substring(separator + 1)
        }
        val query = entries.toMap()
        return query.takeIf { query.size == entries.size }
    }

    private const val MAX_PAYLOAD_LENGTH = 512
    private const val MAX_PORT = 65535
    private val REQUIRED_KEYS = setOf(VERSION_KEY, SCHEME_KEY, INSTANCE_KEY)
    private val SERVER_SCHEMES = setOf("http", "https")
    private val INSTANCE_PATTERN =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
}

private const val PAIRING_SCHEME = "soai-connect"
private const val VERSION_KEY = "v"
private const val SCHEME_KEY = "scheme"
private const val INSTANCE_KEY = "instance"
private const val SUPPORTED_VERSION = "1"
