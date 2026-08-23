// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionClosePolicyTest {
    @Test
    fun authenticationCloseCodeStopsReconnects() {
        assertTrue(SessionClosePolicy.isTerminalAuthenticationClose(4001))
        assertFalse(SessionClosePolicy.isTerminalAuthenticationClose(1001))
        assertFalse(SessionClosePolicy.isTerminalAuthenticationClose(1011))
    }
}
