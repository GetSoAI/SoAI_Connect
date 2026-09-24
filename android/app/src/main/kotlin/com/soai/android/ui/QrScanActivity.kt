// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.soai.android.R
import com.soai.android.databinding.ActivityQrScanBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScanBinding
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        binding = ActivityQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finishCameraUnavailable()
            return
        }
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                try {
                    bindCamera(providerFuture.get())
                } catch (exception: Exception) {
                    Log.w(TAG, "Camera could not be started", exception)
                    finishCameraUnavailable()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onDestroy() {
        imageAnalysis?.clearAnalyzer()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun applySystemBarInsets() {
        val basePaddingTop = binding.toolbar.paddingTop
        val baseHintMarginBottom = binding.hintText.marginBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.toolbar.updatePadding(left = bars.left, top = basePaddingTop + bars.top, right = bars.right)
            binding.hintText.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseHintMarginBottom + bars.bottom
            }
            windowInsets
        }
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        if (isFinishing || isDestroyed) return
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            ANALYSIS_TARGET_SIZE,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .build()
        analysis.setAnalyzer(
            analysisExecutor,
            QrFrameAnalyzer(ContextCompat.getMainExecutor(this), ::showForeignCodeHint, ::deliverPairingCode)
        )
        imageAnalysis = analysis
        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        val camera = provider.bindToLifecycle(this, selector, preview, analysis)
        camera.cameraInfo.cameraState.observe(this) { state ->
            if (state.error?.type == CameraState.ErrorType.CRITICAL) finishCameraUnavailable()
        }
    }

    private fun showForeignCodeHint() {
        if (isFinishing || isDestroyed) return
        binding.hintText.setText(R.string.qr_scan_not_soai)
    }

    private fun deliverPairingCode(raw: String) {
        if (isFinishing || isDestroyed) return
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        binding.root.performHapticFeedback(feedback)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RAW_PAYLOAD, raw))
        finish()
    }

    private fun finishCameraUnavailable() {
        if (isFinishing || isDestroyed) return
        setResult(RESULT_CAMERA_UNAVAILABLE)
        finish()
    }

    companion object {
        const val EXTRA_RAW_PAYLOAD = "com.soai.android.extra.QR_RAW_PAYLOAD"
        const val RESULT_CAMERA_UNAVAILABLE = RESULT_FIRST_USER
        private const val TAG = "QrScanActivity"
        private val ANALYSIS_TARGET_SIZE = Size(1280, 720)
    }
}
