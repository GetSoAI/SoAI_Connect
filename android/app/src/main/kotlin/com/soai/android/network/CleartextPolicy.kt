// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

object CleartextPolicy {

    fun allowsCleartext(
        hostname: String,
        resolveAddresses: (String) -> List<InetAddress> = ::resolveHostAddresses
    ): Boolean {
        val host = hostname.trim().removeSurrounding("[", "]").lowercase()
        if (host.isEmpty()) return false

        val literal = parseLiteralAddress(host)
        if (literal != null) return isPrivateAddress(literal)
        if (isPrivateHostname(host)) return true
        return resolvesToPrivateAddresses(host, resolveAddresses)
    }

    private fun resolvesToPrivateAddresses(
        host: String,
        resolveAddresses: (String) -> List<InetAddress>
    ): Boolean {
        val addresses = try {
            resolveAddresses(host)
        } catch (_: UnknownHostException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
        return addresses.isNotEmpty() && addresses.all { address -> isPrivateAddress(address) }
    }

    private fun resolveHostAddresses(host: String): List<InetAddress> {
        return InetAddress.getAllByName(host).toList()
    }

    private fun isPrivateHostname(host: String): Boolean {
        if (host == LOCALHOST) return true
        return PRIVATE_SUFFIXES.any { suffix -> host.endsWith(suffix) }
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isAnyLocalAddress) {
            return true
        }
        if (address is Inet4Address) {
            return address.isSiteLocalAddress || isSharedAddressSpace(address)
        }
        if (address is Inet6Address) {
            if (address.isSiteLocalAddress) return true
            val firstByte = address.address.firstOrNull()?.toInt()?.and(UNIQUE_LOCAL_MASK)
            return firstByte == UNIQUE_LOCAL_PREFIX
        }
        return false
    }

    private fun isSharedAddressSpace(address: Inet4Address): Boolean {
        val octets = address.address
        val first = octets[0].toInt() and BYTE_MASK
        val second = octets[1].toInt() and BYTE_MASK
        return first == SHARED_FIRST_OCTET && second in SHARED_SECOND_OCTET_RANGE
    }

    private fun parseLiteralAddress(host: String): InetAddress? {
        if (!looksLikeIpv4Literal(host) && !host.contains(':')) return null
        return try {
            InetAddress.getByName(host)
        } catch (_: UnknownHostException) {
            null
        }
    }

    private fun looksLikeIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != IPV4_PART_COUNT) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.length <= IPV4_PART_MAX_DIGITS &&
                part.all { character -> character in '0'..'9' }
        }
    }

    private const val LOCALHOST = "localhost"
    private const val IPV4_PART_COUNT = 4
    private const val IPV4_PART_MAX_DIGITS = 3
    private const val UNIQUE_LOCAL_MASK = 0xFE
    private const val UNIQUE_LOCAL_PREFIX = 0xFC
    private const val BYTE_MASK = 0xFF
    private const val SHARED_FIRST_OCTET = 100

    private val SHARED_SECOND_OCTET_RANGE = 64..127

    private val PRIVATE_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal", ".localhost")
}
