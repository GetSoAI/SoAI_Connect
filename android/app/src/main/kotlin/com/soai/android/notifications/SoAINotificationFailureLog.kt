// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import android.util.Log
import com.soai.android.network.isTlsVerificationFailure
import java.io.IOException

internal object SoAINotificationFailureLog {

    fun webSocketFailure(tag: String, failure: Throwable) {
        if (isExpectedDisconnect(failure)) {
            Log.i(tag, "Notification WebSocket disconnected: ${failure.javaClass.simpleName}")
        } else {
            Log.w(tag, "Notification WebSocket failed", failure)
        }
    }

    fun sessionProbeFailure(tag: String, failure: Exception) {
        if (isExpectedDisconnect(failure)) {
            Log.i(tag, "Session probe after WebSocket failure could not reach the server: ${failure.javaClass.simpleName}")
        } else {
            Log.w(tag, "Session probe after WebSocket failure failed", failure)
        }
    }

    fun isExpectedDisconnect(failure: Throwable): Boolean {
        return failure is IOException && !isTlsVerificationFailure(failure)
    }
}
