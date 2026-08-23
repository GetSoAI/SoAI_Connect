// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivitySettingsBinding
import com.soai.android.network.CertificateUtils
import com.soai.android.network.ServerCertificatePin
import com.soai.android.network.ServerOrigin
import com.soai.android.notifications.SoAINotificationPresenter
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.WebViewDataCleaner
import com.soai.android.web.SoAISessionCookies

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences
    private lateinit var wakeOnLanSettings: WakeOnLanSettings
    private lateinit var sessionController: SettingsSessionController
    private val dialogs = LifecycleDialogRegistry(this)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestNotificationPermissionOrEnable()
        } else {
            binding.androidNotificationsSwitch.isChecked = false
            showMessage(getString(R.string.permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ContentWindowInsets.applyBottom(binding.contentContainer)

        prefs = AppPreferences.getInstance(this)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupSwitches()
        setupButtons()
        wakeOnLanSettings = WakeOnLanSettings(this, binding, prefs, ::showMessage)
        wakeOnLanSettings.initialize()
        sessionController = SettingsSessionController(this, binding, prefs, dialogs, ::showMessage)
        sessionController.initialize()
        updateTrustedCertificateUI()
    }

    override fun onPause() {
        super.onPause()
        if (::binding.isInitialized) {
            wakeOnLanSettings.persist()
        }
    }

    override fun onResume() {
        super.onResume()
        updateActionVisibility()
        updateTrustedCertificateUI()
    }

    private fun setupSwitches() {
        binding.incognitoSwitch.isChecked = prefs.incognitoMode
        binding.incognitoSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.incognitoMode = isChecked
            binding.androidNotificationsSwitch.isEnabled = !isChecked
            if (isChecked) {
                binding.androidNotificationsSwitch.isChecked = false
                WebViewDataCleaner.clearSessionData()
            }
        }

        binding.androidNotificationsSwitch.isEnabled = !prefs.incognitoMode
        binding.androidNotificationsSwitch.isChecked =
            prefs.androidNotificationsEnabled && !prefs.incognitoMode
        binding.androidNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestNotificationPermissionOrEnable()
            } else {
                setAndroidNotificationsEnabled(false)
            }
        }
    }

    private fun requestNotificationPermissionOrEnable() {
        if (prefs.incognitoMode) {
            binding.androidNotificationsSwitch.isChecked = false
            setAndroidNotificationsEnabled(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            setAndroidNotificationsEnabled(true)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            setAndroidNotificationsEnabled(true)
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun setAndroidNotificationsEnabled(enabled: Boolean) {
        prefs.androidNotificationsEnabled = enabled
        if (enabled) {
            prefs.clearNativeNotificationState()
            SoAINotificationServiceController.sync(this)
            showAndroidNotificationsBackgroundDialog()
        } else {
            SoAINotificationPresenter.cancelKnown(this, prefs.notificationSeenIds)
            prefs.clearNativeNotificationState()
            SoAINotificationServiceController.stop(this)
        }
    }

    private fun showAndroidNotificationsBackgroundDialog() {
        dialogs.show(MaterialAlertDialogBuilder(this)
            .setTitle(R.string.android_notifications_background_title)
            .setMessage(R.string.android_notifications_background_message)
            .setPositiveButton(R.string.ok, null))
    }

    private fun setupButtons() {
        binding.resetTrustedCertButton.setOnClickListener {
            dialogs.show(MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_trusted_certificate)
                .setMessage(R.string.reset_trusted_certificate_confirm)
                .setPositiveButton(R.string.reset) { _, _ ->
                    prefs.clearTrustedServerCertificate()
                    sessionController.onTrustedCertificateReset()
                    SoAINotificationServiceController.restart(this)
                    setResult(RESULT_TRUSTED_CERTIFICATE_RESET)
                    updateTrustedCertificateUI()
                    updateActionVisibility()
                    showMessage(getString(R.string.trusted_certificate_cleared))
                }
                .setNegativeButton(R.string.cancel, null))
        }

        binding.clearCacheButton.setOnClickListener {
            dialogs.show(MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_cache)
                .setMessage(R.string.clear_cache_confirm)
                .setPositiveButton(R.string.clear) { _, _ ->
                    WebViewDataCleaner.clearCache()
                    prefs.webCachePopulated = false
                    updateActionVisibility()
                    showMessage(getString(R.string.cache_cleared))
                }
                .setNegativeButton(R.string.cancel, null))
        }

        binding.changeServerButton.setOnClickListener {
            dialogs.show(MaterialAlertDialogBuilder(this)
                .setTitle(R.string.change_server)
                .setMessage(R.string.change_server_confirm)
                .setPositiveButton(R.string.change) { _, _ ->
                    performChangeServer()
                }
                .setNegativeButton(R.string.cancel, null))
        }
    }

    private fun performChangeServer() {
        SoAINotificationPresenter.cancelKnown(this, prefs.notificationSeenIds)
        SoAINotificationServiceController.stop(this)
        WebViewDataCleaner.clearSessionData {
            prefs.clearServerState()
            if (!isDestroyed) AppNavigation.openConnectionAndClearTask(this)
        }
    }

    private fun updateActionVisibility() {
        val serverUrl = prefs.serverUrl
        val hasServer = prefs.hasConnectedServer()
        val cookieHeader = serverUrl?.let { url -> CookieManager.getInstance().getCookie(url) }
        val hasTrustedCertificate = hasApplicableTrustedCertificate()
        val availability = SettingsActionAvailability.resolve(
            hasServer = hasServer,
            hasAuthentication = hasServer && serverUrl != null && SoAISessionCookies.hasAuthentication(serverUrl, cookieHeader),
            hasCache = prefs.webCachePopulated,
            hasTrustedCertificate = hasTrustedCertificate
        )
        binding.logoutButton.visibility = visibleIf(availability.logout)
        binding.clearCacheButton.visibility = visibleIf(availability.clearCache)
        binding.changeServerButton.visibility = visibleIf(availability.changeServer)
        binding.settingsDataDivider.visibility = visibleIf(availability.dataSection)
        binding.settingsDataTitle.visibility = visibleIf(availability.dataSection)
        binding.settingsSecurityTitle.visibility = visibleIf(hasServer)
        binding.trustedCertStatusText.visibility = visibleIf(hasServer)
        binding.resetTrustedCertButton.visibility = visibleIf(availability.resetCertificate)
        binding.androidNotificationsSwitch.visibility = visibleIf(hasServer)
        binding.androidNotificationsDescription.visibility = visibleIf(hasServer)
    }

    private fun visibleIf(visible: Boolean): Int {
        return if (visible) View.VISIBLE else View.GONE
    }

    private fun updateTrustedCertificateUI() {
        val origin = prefs.trustedServerOrigin
        val fingerprint = prefs.trustedServerCertSha256
        if (!hasApplicableTrustedCertificate() || origin == null || fingerprint == null) {
            binding.trustedCertStatusText.text = getString(R.string.no_trusted_certificate)
            return
        }
        binding.trustedCertStatusText.text = getString(
            R.string.trusted_certificate_installed,
            origin,
            CertificateUtils.formatHexFingerprint(fingerprint)
        )
    }

    private fun hasApplicableTrustedCertificate(): Boolean {
        return ServerCertificatePin.isApplicable(
            prefs.serverUrl?.let(ServerOrigin::normalize),
            prefs.serverInstanceId,
            prefs.trustedServerOrigin,
            prefs.trustedServerInstanceId
        ) && ServerCertificatePin.isValidFingerprint(prefs.trustedServerCertSha256)
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        const val RESULT_TRUSTED_CERTIFICATE_RESET = RESULT_FIRST_USER
    }
}

internal data class SettingsActionAvailability(
    val logout: Boolean,
    val clearCache: Boolean,
    val changeServer: Boolean,
    val dataSection: Boolean,
    val resetCertificate: Boolean
) {
    companion object {
        fun resolve(
            hasServer: Boolean,
            hasAuthentication: Boolean,
            hasCache: Boolean,
            hasTrustedCertificate: Boolean
        ): SettingsActionAvailability {
            return SettingsActionAvailability(
                logout = hasAuthentication,
                clearCache = hasCache,
                changeServer = hasServer,
                dataSection = hasAuthentication || hasCache || hasServer,
                resetCertificate = hasTrustedCertificate
            )
        }
    }
}
