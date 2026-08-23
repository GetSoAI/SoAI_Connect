// SPDX-License-Identifier: MIT

package com.soai.android.notifications

internal object ReconnectBackoff {
    const val INITIAL_MILLIS = 1_000L
    const val MAX_MILLIS = 60_000L
    const val STABLE_CONNECTION_MILLIS = 30_000L

    fun next(currentMillis: Long, connectionLifetimeMillis: Long): Long {
        if (connectionLifetimeMillis >= STABLE_CONNECTION_MILLIS) return INITIAL_MILLIS
        return (currentMillis * 2).coerceAtMost(MAX_MILLIS)
    }
}
