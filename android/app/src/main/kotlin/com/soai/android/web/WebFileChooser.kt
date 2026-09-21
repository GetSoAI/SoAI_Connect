// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.soai.android.R
import java.io.File
import java.io.IOException

class WebFileChooser(
    private val activity: AppCompatActivity,
    private val launchIntent: (Intent) -> Unit,
    private val requestCameraPermission: () -> Unit,
    private val onPickerUnavailable: () -> Unit
) {

    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var pendingOwner: WebView? = null
    private var pendingFallbackIntent: Intent? = null
    private var cameraCaptureUri: Uri? = null
    private var cameraCaptureFile: File? = null

    fun show(
        owner: WebView?,
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ) {
        abortPending()
        pendingCallback = callback
        pendingOwner = owner

        try {
            if (!params.isCaptureEnabled || !acceptsImages(params)) {
                launchIntent(params.createIntent())
                return
            }
            if (!hasCameraPermission()) {
                pendingFallbackIntent = params.createIntent()
                requestCameraPermission()
                return
            }
            launchCaptureOrFallback(null)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to launch file chooser", exception)
            abortPending()
            onPickerUnavailable()
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        val fallbackIntent = pendingFallbackIntent
        pendingFallbackIntent = null
        if (pendingCallback == null) return

        try {
            launchCaptureOrFallback(if (granted) null else fallbackIntent)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to launch file chooser", exception)
            abortPending()
            onPickerUnavailable()
        }
    }

    private fun launchCaptureOrFallback(fallbackIntent: Intent?) {
        val intent = fallbackIntent ?: tryCreateCameraCaptureIntent()
        if (intent == null) {
            abortPending()
            onPickerUnavailable()
            return
        }
        launchIntent(intent)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun handleResult(result: ActivityResult) {
        val callback = pendingCallback ?: return
        pendingCallback = null
        pendingOwner = null

        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        val capturedUri = cameraCaptureUri
        val capturedFile = cameraCaptureFile
        cameraCaptureUri = null
        cameraCaptureFile = null

        if (capturedUri != null) {
            revokeCameraUriPermission(capturedUri)
        }

        if (uris != null) {
            if (capturedUri != null && uris.any { uri -> uri == capturedUri }) {
                if (capturedFile?.isFile != true || capturedFile.length() == 0L) {
                    capturedFile?.delete()
                    callback.onReceiveValue(null)
                    return
                }
                callback.onReceiveValue(uris)
                return
            }
            capturedFile?.delete()
            callback.onReceiveValue(uris)
            return
        }

        if (result.resultCode == AppCompatActivity.RESULT_OK && capturedUri != null) {
            if (capturedFile?.isFile != true || capturedFile.length() == 0L) {
                capturedFile?.delete()
                callback.onReceiveValue(null)
                return
            }
            callback.onReceiveValue(arrayOf(capturedUri))
            return
        }

        capturedFile?.delete()
        callback.onReceiveValue(null)
    }

    fun cancelFor(owner: WebView) {
        if (pendingOwner != owner) return
        abortPending()
    }

    fun cancelAll() {
        abortPending()
    }

    private fun abortPending() {
        val capturedUri = cameraCaptureUri
        if (capturedUri != null) {
            revokeCameraUriPermission(capturedUri)
        }
        cameraCaptureFile?.delete()
        cameraCaptureUri = null
        cameraCaptureFile = null
        pendingFallbackIntent = null
        pendingCallback?.onReceiveValue(null)
        pendingCallback = null
        pendingOwner = null
    }

    private fun tryCreateCameraCaptureIntent(): Intent? {
        val captureDirectory = File(activity.cacheDir, CameraCaptureFiles.DIRECTORY)
        if (!captureDirectory.exists() && !captureDirectory.mkdirs()) {
            Log.w(TAG, "Unable to create capture cache directory")
            return null
        }
        CameraCaptureFiles.pruneStale(captureDirectory, System.currentTimeMillis())
        val photoFile = try {
            CameraCaptureFiles.create(captureDirectory)
        } catch (exception: IOException) {
            Log.w(TAG, "Unable to create capture file", exception)
            return null
        }

        val uri = try {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", photoFile)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Unable to create capture content URI", exception)
            photoFile.delete()
            return null
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newUri(activity.contentResolver, activity.getString(R.string.app_name), uri)
        }

        val resolved = activity.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (resolved.isEmpty()) {
            revokeCameraUriPermission(uri)
            photoFile.delete()
            return null
        }

        for (resolveInfo in resolved) {
            activity.grantUriPermission(
                resolveInfo.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        cameraCaptureUri = uri
        cameraCaptureFile = photoFile
        return intent
    }

    private fun revokeCameraUriPermission(uri: Uri) {
        activity.revokeUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private fun acceptsImages(params: WebChromeClient.FileChooserParams): Boolean {
        val types = params.acceptTypes
            ?.map { acceptType -> acceptType.trim().lowercase() }
            ?.filter { acceptType -> acceptType.isNotBlank() }
            ?: emptyList()

        if (types.isEmpty()) return true
        return types.any { acceptType -> acceptType == "image/*" || acceptType.startsWith("image/") }
    }

    private companion object {
        const val TAG = "WebFileChooser"
    }
}
