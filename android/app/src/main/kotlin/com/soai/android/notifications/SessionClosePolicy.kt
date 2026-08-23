// SPDX-License-Identifier: MIT

package com.soai.android.notifications

object SessionClosePolicy {
    fun isTerminalAuthenticationClose(code: Int): Boolean = code == 4001
}

enum class SessionConnectionOutcome {
    RECONNECT,
    AUTHENTICATION_REVOKED
}
