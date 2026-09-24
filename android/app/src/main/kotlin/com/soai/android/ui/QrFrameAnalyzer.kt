// SPDX-License-Identifier: MIT

package com.soai.android.ui

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.soai.android.network.ConnectPairingPayloadParser
import com.soai.android.network.PairingScan
import com.soai.android.network.QrLuminanceDecoder
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal class QrFrameAnalyzer(
    private val callbackExecutor: Executor,
    private val onForeignCode: () -> Unit,
    private val onPairingCode: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val decoder = QrLuminanceDecoder()
    private val delivered = AtomicBoolean(false)
    private val foreignCodeReported = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        try {
            if (delivered.get()) return
            val plane = image.planes.firstOrNull() ?: return
            val raw = decoder.decode(plane.buffer, plane.rowStride, image.width, image.height) ?: return
            when (ConnectPairingPayloadParser.parse(raw)) {
                PairingScan.Ignored -> if (foreignCodeReported.compareAndSet(false, true)) {
                    callbackExecutor.execute(onForeignCode)
                }
                is PairingScan.Valid, PairingScan.Unsupported -> if (delivered.compareAndSet(false, true)) {
                    callbackExecutor.execute { onPairingCode(raw) }
                }
            }
        } finally {
            image.close()
        }
    }
}
