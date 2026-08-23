// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal object SoAIWindowSystemBars {

    fun hideStatusBar(window: Window) {
        WindowCompat.enableEdgeToEdge(window)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    }
}
