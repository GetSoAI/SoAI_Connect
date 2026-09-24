// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.soai.android.network.ConnectPairingPayload
import com.soai.android.network.ConnectPairingPayloadParser
import com.soai.android.network.PairingScan

internal sealed interface QrScanOutcome {
    data class Scanned(val payload: ConnectPairingPayload) : QrScanOutcome
    data object Unsupported : QrScanOutcome
    data object CameraUnavailable : QrScanOutcome
    data object Cancelled : QrScanOutcome
}

internal class QrScanContract : ActivityResultContract<Unit, QrScanOutcome>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(context, QrScanActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): QrScanOutcome {
        if (resultCode == QrScanActivity.RESULT_CAMERA_UNAVAILABLE) return QrScanOutcome.CameraUnavailable
        if (resultCode != Activity.RESULT_OK) return QrScanOutcome.Cancelled
        val raw = intent?.getStringExtra(QrScanActivity.EXTRA_RAW_PAYLOAD) ?: return QrScanOutcome.Cancelled
        return when (val scan = ConnectPairingPayloadParser.parse(raw)) {
            is PairingScan.Valid -> QrScanOutcome.Scanned(scan.payload)
            PairingScan.Unsupported -> QrScanOutcome.Unsupported
            PairingScan.Ignored -> QrScanOutcome.Cancelled
        }
    }
}
