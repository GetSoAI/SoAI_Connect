// Notification session close and authentication outcomes for notifications/SessionClosePolicy.kt.
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
    NETWORK_CHANGED,
    AUTHENTICATION_REVOKED,
    FAILED
}
