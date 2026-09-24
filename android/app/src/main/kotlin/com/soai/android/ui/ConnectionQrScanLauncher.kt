// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.soai.android.R
import com.soai.android.network.ConnectPairingPayload

internal class ConnectionQrScanLauncher(
    private val activity: AppCompatActivity,
    private val onScanned: (ConnectPairingPayload) -> Unit,
    private val onError: (String) -> Unit
) {
    private var inFlight = false

    private val scanLauncher = activity.registerForActivityResult(QrScanContract()) { outcome ->
        inFlight = false
        when (outcome) {
            is QrScanOutcome.Scanned -> onScanned(outcome.payload)
            QrScanOutcome.Unsupported -> onError(activity.getString(R.string.qr_scan_unsupported))
            QrScanOutcome.CameraUnavailable -> onError(activity.getString(R.string.qr_scan_camera_unavailable))
            QrScanOutcome.Cancelled -> {}
        }
    }

    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scanLauncher.launch(Unit)
            } else {
                inFlight = false
                onError(activity.getString(R.string.qr_scan_camera_permission_denied))
            }
        }

    val isAvailable: Boolean
        get() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun bind(layout: TextInputLayout) {
        layout.setEndIconOnClickListener { launch() }
    }

    private fun launch() {
        if (inFlight) return
        inFlight = true
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            scanLauncher.launch(Unit)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
