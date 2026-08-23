// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun shortConnectionsGrowExponentiallyAndCap() {
        assertEquals(2_000L, ReconnectBackoff.next(1_000L, 1L))
        assertEquals(60_000L, ReconnectBackoff.next(60_000L, 0L))
    }

    @Test
    fun stableConnectionResetsBackoff() {
        assertEquals(1_000L, ReconnectBackoff.next(32_000L, 30_000L))
    }
}
