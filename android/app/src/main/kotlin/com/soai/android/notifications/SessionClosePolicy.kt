// SPDX-License-Identifier: MIT

package com.soai.android.notifications

object SessionClosePolicy {
    fun indicatesAuthenticationLoss(code: Int): Boolean = code == 4001

    fun outcomeAfterAuthenticationProbe(authenticated: Boolean?): SessionConnectionOutcome {
        return if (authenticated == false) {
            SessionConnectionOutcome.AUTHENTICATION_REVOKED
        } else {
            SessionConnectionOutcome.RECONNECT
        }
    }
}

enum class SessionConnectionOutcome {
    RECONNECT,
    AUTHENTICATION_REVOKED
}
