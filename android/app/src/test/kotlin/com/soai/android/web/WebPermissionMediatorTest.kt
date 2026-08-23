/* SoAI - WebView permission mediator tests [soai_connect/android/app/src/test/kotlin/com/soai/android/web/WebPermissionMediatorTest.kt] */
// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.webkit.PermissionRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPermissionMediatorTest {

    @Test
    fun grantsPendingWebViewRequestAfterRuntimePermissionResult() {
        val launchedPermissions = mutableListOf<Array<String>>()
        var grantedResources: Array<String>? = null
        var cameraPermissionGranted = false
        val permissionRequest = FakePermissionRequest(
            resources = arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            onGrant = { resources -> grantedResources = resources }
        )
        val mediator = createMediator(
            launchedPermissions = launchedPermissions,
            runtimePermissionGranted = { cameraPermissionGranted }
        )

        mediator.handleRequest(permissionRequest)

        assertTrue(mediator.hasPendingRequest())
        assertArrayEquals(arrayOf(android.Manifest.permission.CAMERA), launchedPermissions.single())

        cameraPermissionGranted = true
        mediator.onRuntimePermissionsResult(mapOf(android.Manifest.permission.CAMERA to true))

        assertFalse(mediator.hasPendingRequest())
        assertArrayEquals(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE), grantedResources)
    }

    @Test
    fun deniesPendingWebViewRequestWhenRuntimePermissionIsDenied() {
        var denied = false
        val permissionRequest = FakePermissionRequest(
            resources = arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            onDeny = { denied = true }
        )
        val mediator = createMediator(runtimePermissionGranted = { false })

        mediator.handleRequest(permissionRequest)
        mediator.onRuntimePermissionsResult(mapOf(android.Manifest.permission.CAMERA to false))

        assertFalse(mediator.hasPendingRequest())
        assertTrue(denied)
    }

    @Test
    fun doesNotGrantCanceledWebViewRequestAfterRuntimePermissionResult() {
        var granted = false
        var cameraPermissionGranted = false
        val permissionRequest = FakePermissionRequest(
            resources = arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            onGrant = { granted = true }
        )
        val mediator = createMediator(runtimePermissionGranted = { cameraPermissionGranted })

        mediator.handleRequest(permissionRequest)
        mediator.handleCanceled(permissionRequest)
        cameraPermissionGranted = true
        mediator.onRuntimePermissionsResult(mapOf(android.Manifest.permission.CAMERA to true))

        assertFalse(mediator.hasPendingRequest())
        assertFalse(granted)
    }

    private fun createMediator(
        launchedPermissions: MutableList<Array<String>> = mutableListOf(),
        runtimePermissionGranted: (String) -> Boolean
    ): WebPermissionMediator {
        return WebPermissionMediator(
            launchRuntimePermissions = { permissions -> launchedPermissions.add(permissions) },
            serverUri = { null },
            onDenied = {},
            runtimePermissionGranted = runtimePermissionGranted,
            isTrustedOrigin = { true },
            writeWarning = {},
            writeDebug = {}
        )
    }

    private class FakePermissionRequest(
        private val resources: Array<String>,
        private val onGrant: (Array<String>) -> Unit = {},
        private val onDeny: () -> Unit = {}
    ) : PermissionRequest() {
        override fun getOrigin() = null

        override fun getResources(): Array<String> {
            return resources
        }

        override fun grant(resources: Array<String>) {
            onGrant(resources)
        }

        override fun deny() {
            onDeny()
        }
    }
}
