// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.Manifest
import android.net.Uri
import android.util.Log
import android.webkit.PermissionRequest
import com.soai.android.network.ServerOrigin

class WebPermissionMediator(
    private val launchRuntimePermissions: (Array<String>) -> Unit,
    private val serverUri: () -> Uri?,
    private val runtimePermissionGranted: (String) -> Boolean,
    private val isTrustedOrigin: (PermissionRequest) -> Boolean = { request ->
        val server = serverUri()
        val origin = request.origin
        server != null && origin != null && ServerOrigin.isSameOrigin(origin, server)
    },
    private val writeWarning: (String) -> Unit = { message -> Log.w(TAG, message) },
    private val writeDebug: (String) -> Unit = { message -> Log.d(TAG, message) }
) {

    private var pendingRequest: PermissionRequest? = null
    private var pendingRuntimePermissions: Set<String> = emptySet()

    fun hasPendingRequest(): Boolean {
        return pendingRequest != null
    }

    fun handleRequest(request: PermissionRequest) {
        if (!isTrustedOrigin(request)) {
            writeWarning("Rejected WebView permission request for an untrusted origin")
            request.deny()
            return
        }

        val runtimePermissions = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                else -> null
            }
        }.distinct()

        if (runtimePermissions.isEmpty()) {
            writeWarning("Rejected WebView permission request with no supported capture resource")
            request.deny()
            return
        }

        if (pendingRequest != null) {
            writeWarning("Rejected overlapping WebView permission request")
            request.deny()
            return
        }

        val allGranted = runtimePermissions.all(runtimePermissionGranted)

        if (allGranted) {
            writeDebug("Granted WebView capture request from existing runtime permissions")
            grantCaptureResources(request)
            return
        }

        pendingRequest = request
        pendingRuntimePermissions = runtimePermissions.toSet()
        writeDebug("Waiting for Android runtime permission result for WebView capture request")
        launchRuntimePermissions(runtimePermissions.toTypedArray())
    }

    fun handleCanceled(request: PermissionRequest?) {
        if (pendingRequest == request) {
            writeDebug("WebView canceled its pending capture permission request")
            pendingRequest = null
            pendingRuntimePermissions = emptySet()
        }
    }

    fun onRuntimePermissionsResult(grants: Map<String, Boolean>) {
        val request = pendingRequest ?: run {
            writeDebug("Ignoring runtime permission result without a pending WebView request")
            return
        }
        val expectedPermissions = pendingRuntimePermissions
        pendingRequest = null
        pendingRuntimePermissions = emptySet()

        val allGranted = expectedPermissions.isNotEmpty() && expectedPermissions.all { permission ->
            grants[permission] == true || runtimePermissionGranted(permission)
        }
        if (!allGranted) {
            writeDebug("Android runtime permission denied for WebView capture request")
            request.deny()
            return
        }

        writeDebug("Android runtime permission granted; granting WebView capture request")
        grantCaptureResources(request)
    }

    fun cancelPending() {
        if (pendingRequest != null) {
            writeDebug("Canceling pending WebView capture permission request")
        }
        pendingRequest?.deny()
        pendingRequest = null
        pendingRuntimePermissions = emptySet()
    }

    private fun grantCaptureResources(request: PermissionRequest) {
        val allowedResources = request.resources.filter { resource ->
            resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE
        }.toTypedArray()

        if (allowedResources.isEmpty()) {
            request.deny()
            return
        }

        request.grant(allowedResources)
    }

    private companion object {
        const val TAG = "WebPermissionMediator"
    }
}
