// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal object ContentWindowInsets {

    fun applyBottom(content: View) {
        val basePaddingLeft = content.paddingLeft
        val basePaddingRight = content.paddingRight
        val basePaddingBottom = content.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(content) { target, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            target.setPadding(
                basePaddingLeft + bars.left,
                target.paddingTop,
                basePaddingRight + bars.right,
                basePaddingBottom + maxOf(bars.bottom, ime.bottom)
            )
            consumeBottomAndSides(windowInsets, bars, ime)
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun consumeBottomAndSides(
        windowInsets: WindowInsetsCompat,
        bars: Insets,
        ime: Insets
    ): WindowInsetsCompat {
        val remainingBars = Insets.of(0, bars.top, 0, 0)
        return WindowInsetsCompat.Builder(windowInsets)
            .setInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                remainingBars
            )
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, ime.top, 0, 0))
            .build()
    }
}
