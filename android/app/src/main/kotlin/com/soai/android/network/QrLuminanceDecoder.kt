// SPDX-License-Identifier: MIT

package com.soai.android.network

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

internal class QrLuminanceDecoder {
    private val reader = QRCodeReader()

    fun decode(plane: ByteBuffer, rowStride: Int, width: Int, height: Int): String? {
        val luminance = copyRows(plane, rowStride, width, height) ?: return null
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), DECODE_HINTS).text
        } catch (_: NotFoundException) {
            null
        } catch (_: ChecksumException) {
            null
        } catch (_: FormatException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun copyRows(plane: ByteBuffer, rowStride: Int, width: Int, height: Int): ByteArray? {
        if (width <= 0 || height <= 0 || rowStride < width) return null
        val source = plane.duplicate()
        val start = source.position()
        if (source.limit() - start < rowStride.toLong() * (height - 1) + width) return null
        val luminance = ByteArray(width * height)
        for (row in 0 until height) {
            source.position(start + row * rowStride)
            source.get(luminance, row * width, width)
        }
        return luminance
    }

    private companion object {
        val DECODE_HINTS = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
    }
}
