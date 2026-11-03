// Device identity updates and session revocation from settings for ui/SettingsSessionController.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivitySettingsBinding
import com.soai.android.network.HttpClientFactory
import com.soai.android.network.HttpClientRelease
import com.soai.android.network.SoAISessionApi
import com.soai.android.network.SessionActionResult
import com.soai.android.notifications.SoAINotificationPresenter
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.AndroidDeviceIdentity
import com.soai.android.web.AndroidDeviceIdentityCookie
import com.soai.android.web.SoAISessionCookies
import com.soai.android.web.WebViewDataCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class SettingsSessionController(
    private val activity: SettingsActivity,
    private val binding: ActivitySettingsBinding,
    private val prefs: AppPreferences,
    private val dialogs: LifecycleDialogRegistry,
    private val showMessage: (String) -> Unit
) : DefaultLifecycleObserver {
    private var httpClient = HttpClientFactory.createPinnedRest(prefs)
    private var sessionApi = SoAISessionApi(httpClient, CookieManager.getInstance())

    init {
        activity.lifecycle.addObserver(this)
    }

    fun initialize() {
        binding.deviceNameInput.setText(prefs.deviceLabel)
        binding.saveDeviceNameButton.setOnClickListener { saveDeviceName() }
        binding.logoutButton.setOnClickListener { confirmLogout() }
    }

    fun onTrustedCertificateReset() {
        closeHttpClient()
        httpClient = HttpClientFactory.createPinnedRest(prefs)
        sessionApi = SoAISessionApi(httpClient, CookieManager.getInstance())
    }

    private fun saveDeviceName() {
        val label = AndroidDeviceIdentity.normalizeLabel(
            binding.deviceNameInput.text?.toString().orEmpty()
        )
        if (label.isEmpty()) {
            binding.deviceNameLayout.error = activity.getString(R.string.device_name_required)
            return
        }
        binding.deviceNameLayout.error = null
        val serverUrl = prefs.serverUrl
        val authenticated = prefs.hasConnectedServer() && serverUrl != null && SoAISessionCookies.hasAuthentication(
            serverUrl,
            CookieManager.getInstance().getCookie(serverUrl)
        )
        if (serverUrl == null || !authenticated) {
            persistDeviceLabel(label, serverUrl)
            return
        }
        binding.saveDeviceNameButton.isEnabled = false
        activity.lifecycleScope.launch {
            try {
                when (val result = sessionApi.renameCurrentDevice(serverUrl, prefs.deviceId, label)) {
                    SessionActionResult.Success -> persistDeviceLabel(label, serverUrl)
                    is SessionActionResult.Failure -> showMessage(describeFailure(result))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Device name update failed", exception)
                showMessage(activity.getString(R.string.device_name_update_failed))
            } finally {
                binding.saveDeviceNameButton.isEnabled = true
            }
        }
    }

    private fun persistDeviceLabel(label: String, serverUrl: String?) {
        prefs.deviceLabel = label
        if (serverUrl == null) {
            showMessage(activity.getString(R.string.device_name_saved))
            return
        }
        AndroidDeviceIdentityCookie.install(
            CookieManager.getInstance(),
            serverUrl,
            prefs.deviceIdentity()
        ) { installed ->
            if (activity.isDestroyed || activity.isFinishing) return@install
            if (installed) {
                showMessage(activity.getString(R.string.device_name_saved))
            } else {
                Log.e(TAG, "Device identity cookie update failed")
                showMessage(activity.getString(R.string.device_identity_cookie_failed))
            }
        }
    }

    private fun confirmLogout() {
        dialogs.show(
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.logout) { _, _ -> revokeAndLogout() }
                .setNegativeButton(R.string.cancel, null)
        )
    }

    private fun revokeAndLogout() {
        val serverUrl = prefs.serverUrl ?: return
        binding.logoutButton.isEnabled = false
        activity.lifecycleScope.launch {
            try {
                when (val result = sessionApi.logout(serverUrl)) {
                    SessionActionResult.Success -> clearLocalSession()
                    is SessionActionResult.Failure -> showLogoutFailure(describeFailure(result))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Server logout failed", exception)
                showLogoutFailure()
            } finally {
                binding.logoutButton.isEnabled = true
            }
        }
    }

    private fun describeFailure(failure: SessionActionResult.Failure): String {
        val message = activity.getString(failure.messageResource)
        return failure.traceId?.let { "$message\n$it" } ?: message
    }

    private fun showLogoutFailure(cause: String? = null) {
        if (activity.isDestroyed || activity.isFinishing) return
        dialogs.show(
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.logout_failed_title)
                .setMessage(listOfNotNull(cause, activity.getString(R.string.logout_failed_message)).joinToString("\n\n"))
                .setPositiveButton(R.string.retry) { _, _ -> revokeAndLogout() }
                .setNegativeButton(R.string.clear_locally) { _, _ -> confirmLocalClear() }
                .setNeutralButton(R.string.cancel, null)
        )
    }

    private fun confirmLocalClear() {
        dialogs.show(
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.clear_locally)
                .setMessage(R.string.clear_locally_warning)
                .setPositiveButton(R.string.clear_locally) { _, _ -> clearLocalSession() }
                .setNegativeButton(R.string.cancel, null)
        )
    }

    private fun clearLocalSession() {
        SoAINotificationPresenter.cancelKnown(activity, prefs.notificationSeenIds)
        SoAINotificationServiceController.stop(activity)
        prefs.clearNativeNotificationState()
        prefs.webCachePopulated = false
        WebViewDataCleaner.clearSessionData {
            if (!activity.isDestroyed) AppNavigation.openMainAndClearTask(activity)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        closeHttpClient()
        owner.lifecycle.removeObserver(this)
    }

    private fun closeHttpClient() {
        HttpClientRelease.release(httpClient)
    }

    companion object {
        private const val TAG = "SettingsSession"
    }
}
