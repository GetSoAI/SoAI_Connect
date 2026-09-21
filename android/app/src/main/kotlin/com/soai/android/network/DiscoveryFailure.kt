// SPDX-License-Identifier: MIT

package com.soai.android.network

enum class DiscoveryFailureReason {
    INVALID_ADDRESS,
    IPV6_ZONE_UNSUPPORTED,
    INVALID_PORT,
    NOT_REACHABLE,
    TEMPORARILY_UNAVAILABLE,
    NOT_FOUND,
    AMBIGUOUS,
    NOT_SOAI_SERVER
}

class DiscoveryException(
    val reason: DiscoveryFailureReason,
    val detail: String? = null
) : Exception(if (detail == null) reason.name else "${reason.name}: $detail")
