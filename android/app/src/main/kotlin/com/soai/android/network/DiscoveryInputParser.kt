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
        val separator = input.indexOf("://")
        val scheme = input.substring(0, separator).lowercase()
        if (scheme != HTTP_SCHEME && scheme != HTTPS_SCHEME) return invalidInput()
        val remainder = input.substring(separator + 3)
        val authority = remainder.substringBefore('/').substringBefore('?').substringBefore('#')
        if (!authority.startsWith('[') && authority.count { character -> character == ':' } > 1) {
            return invalidInput()
        }
        val parsed = parseAuthority(authority, asciiPort = true) ?: return invalidInput()
        val hostAuthority = if (parsed.port == null) authority else authority.substringBeforeLast(':')
        val uri = try {
            URI("$scheme://$hostAuthority${remainder.substring(authority.length)}")
        } catch (_: URISyntaxException) {
            return invalidInput()
        }
        if (uri.host == null || uri.rawUserInfo != null) return invalidInput()
        val port = parsed.port ?: if (scheme == HTTPS_SCHEME) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
        return DiscoveryInput(parsed.hostname, port, scheme)
    }

    private fun parseAuthority(input: String, asciiPort: Boolean = false): ParsedAuthority? {
        if (input.isBlank() || input.contains('@')) return null
        if (input.startsWith('[')) {
            val closingBracket = input.indexOf(']')
            if (closingBracket <= 1) return null
            val hostname = normalizeHostname(input.substring(1, closingBracket)) ?: return null
            val suffix = input.substring(closingBracket + 1)
            if (suffix.isEmpty()) return ParsedAuthority(hostname, null)
            if (!suffix.startsWith(':')) return null
            val portText = suffix.substring(1)
            return parsePort(portText, asciiPort)?.let { port -> ParsedAuthority(hostname, port) }
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
        return parsePort(portText, asciiPort)?.let { port -> ParsedAuthority(hostname, port) }
    }

    private fun parsePort(portText: String, asciiOnly: Boolean): Int? {
        if (portText.any { character ->
                if (asciiOnly) character !in '0'..'9' else !character.isDigit()
            }
        ) return null
        return portText.toIntOrNull()?.takeIf { port -> port in 1..65535 }
            ?: throw DiscoveryException(DiscoveryFailureReason.INVALID_PORT, portText.ifEmpty { ":" })
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
