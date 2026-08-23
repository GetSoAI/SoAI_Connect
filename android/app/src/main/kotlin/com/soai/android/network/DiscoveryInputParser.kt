// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException

internal data class DiscoveryInput(
    val hostname: String,
    val explicitPort: Int?,
    val preferredScheme: String?
)

internal object DiscoveryInputParser {
    fun hasIpv6ZoneIdentifier(input: String): Boolean {
        val trimmed = input.trim()
        val authority = if (trimmed.contains("://")) {
            trimmed.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        } else {
            trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
        }
        if (authority.contains('@')) return false
        val bracketStart = authority.indexOf('[')
        if (bracketStart >= 0) {
            val bracketEnd = authority.indexOf(']', startIndex = bracketStart + 1)
                .takeIf { index -> index >= 0 }
                ?: authority.length
            val bracketedHost = authority.substring(bracketStart + 1, bracketEnd)
            return bracketedHost.contains(':') && bracketedHost.contains('%')
        }
        return authority.count { character -> character == ':' } > 1 && authority.contains('%')
    }

    fun parse(input: String): DiscoveryInput {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return invalidInput()
        if (trimmed.contains(Regex("\\s"))) return invalidInput()

        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator >= 0) {
            return parseUrl(trimmed)
        }

        val authority = trimmed
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val parsedAuthority = parseAuthority(authority) ?: return invalidInput()
        return DiscoveryInput(parsedAuthority.hostname, parsedAuthority.port, null)
    }

    fun formatHost(hostname: String): String {
        val trimmed = hostname.trim().removeSurrounding("[", "]")
        return if (trimmed.contains(':')) "[$trimmed]" else trimmed
    }

    private fun parseUrl(input: String): DiscoveryInput {
        val uri = try {
            URI(input)
        } catch (_: URISyntaxException) {
            return invalidInput()
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != HTTP_SCHEME && scheme != HTTPS_SCHEME) return invalidInput()
        if (uri.rawUserInfo != null) return invalidInput()
        val hostname = uri.host?.let { host -> normalizeHostname(host) } ?: return invalidInput()
        val port = when {
            uri.port != -1 -> uri.port
            scheme == HTTPS_SCHEME -> HTTPS_DEFAULT_PORT
            else -> HTTP_DEFAULT_PORT
        }
        return DiscoveryInput(hostname, port, scheme)
    }

    private fun parseAuthority(input: String): ParsedAuthority? {
        if (input.isBlank() || input.contains('@')) return null
        if (input.startsWith('[')) {
            val closingBracket = input.indexOf(']')
            if (closingBracket <= 1) return null
            val hostname = normalizeHostname(input.substring(1, closingBracket)) ?: return null
            val suffix = input.substring(closingBracket + 1)
            if (suffix.isEmpty()) return ParsedAuthority(hostname, null)
            if (!suffix.startsWith(':')) return null
            val portText = suffix.substring(1)
            if (portText.isEmpty() || portText.any { character -> !character.isDigit() }) return null
            return ParsedAuthority(hostname, portText.toIntOrNull())
        }
        if (input.contains('[') || input.contains(']')) return null
        if (input.count { character -> character == ':' } > 1) {
            val hostname = normalizeHostname(input) ?: return null
            return ParsedAuthority(hostname, null)
        }
        val separator = input.lastIndexOf(':')
        if (separator < 0) {
            return normalizeHostname(input)?.let { hostname -> ParsedAuthority(hostname, null) }
        }
        val hostname = normalizeHostname(input.substring(0, separator)) ?: return null
        val portText = input.substring(separator + 1)
        if (portText.isEmpty() || portText.any { character -> !character.isDigit() }) return null
        return ParsedAuthority(hostname, portText.toIntOrNull())
    }

    private fun normalizeHostname(value: String): String? {
        val trimmed = value.trim().removeSurrounding("[", "]")
        if (trimmed.isEmpty() || trimmed.contains('%')) return null
        if (trimmed.contains(':')) {
            val address = try {
                InetAddress.getByName(trimmed)
            } catch (_: UnknownHostException) {
                return null
            }
            if (address !is Inet6Address) return null
            return trimmed.lowercase()
        }
        val ascii = try {
            IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (ascii.length > MAX_HOSTNAME_LENGTH || ascii.isBlank()) return null
        val labels = ascii.split('.')
        if (labels.any { label -> label.isEmpty() || label.length > MAX_LABEL_LENGTH || label.startsWith('-') || label.endsWith('-') }) return null
        return ascii.lowercase()
    }

    private fun invalidInput(): DiscoveryInput {
        return DiscoveryInput("", null, null)
    }

    private data class ParsedAuthority(val hostname: String, val port: Int?)

    private const val HTTP_SCHEME = "http"
    private const val HTTPS_SCHEME = "https"
    private const val HTTP_DEFAULT_PORT = 80
    private const val HTTPS_DEFAULT_PORT = 443
    private const val MAX_HOSTNAME_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
}
