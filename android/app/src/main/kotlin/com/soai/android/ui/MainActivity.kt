// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import com.soai.android.R
import com.soai.android.SoAIApplication
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivityMainBinding
import com.soai.android.notifications.SoAINotificationBridgeController
import com.soai.android.notifications.SoAINotificationLaunchRouter
import com.soai.android.web.AndroidDeviceIdentityCookie
import com.soai.android.web.PopupWebViewController
import com.soai.android.web.SoAIWebChromeClientFactory
import com.soai.android.web.SoAIWebViewClientFactory
import com.soai.android.web.WebFileChooser
import com.soai.android.web.WebPermissionMediator
import com.soai.android.web.WebViewDataCleaner
import com.soai.android.web.WebViewHost

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var mainHost: WebViewHost
    private lateinit var trustController: ServerTrustController
    private lateinit var notificationBridgeController: SoAINotificationBridgeController
    private lateinit var pageOwner: MainWebPageOwner
    private var configuredIncognitoMode = false
    private var pageLoadGeneration = 0L
    private val dialogs = LifecycleDialogRegistry(this)

    private val fileChooserLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> fileChooser.handleResult(result) }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == SettingsActivity.RESULT_TRUSTED_CERTIFICATE_RESET) {
            pageOwner.onTrustedCertificateReset()
        }
    }

    private val runtimePermissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants -> permissionMediator.onRuntimePermissionsResult(grants) }

    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> fileChooser.onCameraPermissionResult(granted) }

    private val fileChooser = WebFileChooser(
        activity = this,
        launchIntent = { intent -> fileChooserLauncher.launch(intent) },
        requestCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
        onPickerUnavailable = { showSnackbar(getString(R.string.error_opening_file_picker)) }
    )

    private val permissionMediator = WebPermissionMediator(
        launchRuntimePermissions = { permissions -> runtimePermissionLauncher.launch(permissions) },
        serverUri = { serverUri },
        onDenied = { showSnackbar(getString(R.string.permission_denied)) },
        runtimePermissionGranted = { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    )

    private val launchRouter = SoAINotificationLaunchRouter(
        activity = this,
        openExternalUrl = { uri -> pageOwner.openExternalUrl(uri) }
    )

    private val popupController = PopupWebViewController(
        activity = this,
        configureHost = { host, onTitleChanged ->
            configureWebViewHost(host, isMain = false, onTitleChanged = onTitleChanged)
        },
        onPopupDismissed = { webView ->
            fileChooser.cancelFor(webView)
            trustController.cancelFor(webView)
        }
    )

    private val serverUri: Uri?
        get() = prefs.serverUrl?.toUri()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoAIWindowSystemBars.hideStatusBar(window)
        prefs = AppPreferences.getInstance(this)
        if (!prefs.hasConnectedServer()) {
            prefs.clearServerState()
            AppNavigation.openConnectionAndClearTask(this)
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ContentWindowInsets.applyBottom(binding.contentContainer)

        trustController = ServerTrustController(this, prefs)
        notificationBridgeController = SoAINotificationBridgeController(this, prefs)
        pageOwner = MainWebPageOwner(
            activity = this,
            prefs = prefs,
            dialogs = dialogs,
            popupController = popupController,
            trustController = trustController,
            notificationBridgeController = notificationBridgeController,
            mainHost = { mainHost },
            recreateMainWebView = { loadImmediately -> recreateMainWebView(loadImmediately) },
            showMessage = ::showSnackbar
        )
        configuredIncognitoMode = prefs.incognitoMode

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_overflow_horiz)
        createMainWebView()
        setupBackPressHandler()
        loadWebUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!::mainHost.isInitialized) return
        val serverUrl = prefs.serverUrl ?: return
        launchRouter.dispatch(mainHost.webView, intent, serverUrl)
    }

    override fun onStart() {
        super.onStart()
        if (!::mainHost.isInitialized) return
        if (configuredIncognitoMode != prefs.incognitoMode) {
            configuredIncognitoMode = prefs.incognitoMode
            popupController.dismissAll()
            trustController.resetSessionTrust()
            WebViewDataCleaner.clearSessionData {
                if (!isDestroyed) recreateMainWebView()
            }
            return
        }
        if (!prefs.incognitoMode) return
        if (!SoAIApplication.instance.consumeIncognitoInvalidation()) return

        popupController.dismissAll()
        trustController.resetSessionTrust()
        WebViewDataCleaner.clearSessionData()
        loadWebUI()
    }

    override fun onPause() {
        if (::trustController.isInitialized) trustController.onHostPaused()
        if (::mainHost.isInitialized) {
            if (!permissionMediator.hasPendingRequest()) {
                mainHost.webView.onPause()
            }
            popupController.pauseAll()
        }
        super.onPause()
        if (!prefs.incognitoMode) {
            CookieManager.getInstance().flush()
        }
    }

    override fun onResume() {
        super.onResume()
        SoAIWindowSystemBars.hideStatusBar(window)
        if (::mainHost.isInitialized) {
            mainHost.webView.onResume()
            popupController.resumeAll()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (::trustController.isInitialized) trustController.onHostResumed()
    }

    override fun onStop() {
        super.onStop()
        if (!::mainHost.isInitialized) return
        if (!permissionMediator.hasPendingRequest()) {
            mainHost.webView.onPause()
        }
        if (!prefs.incognitoMode) return
        if (!isFinishing) return
        WebViewDataCleaner.clearSessionData()
    }

    override fun onDestroy() {
        pageLoadGeneration += 1L
        fileChooser.cancelAll()
        permissionMediator.cancelPending()
        if (::notificationBridgeController.isInitialized) notificationBridgeController.detach()
        popupController.dismissAll()
        if (::trustController.isInitialized) {
            trustController.destroy()
        }
        if (::mainHost.isInitialized) {
            mainHost.destroy()
        }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_reload -> {
                mainHost.hardReload()
                true
            }
            R.id.menu_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mainHost.webView.canGoBack()) {
                    mainHost.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun createMainWebView() {
        mainHost = MainWebViewHostFactory.create(this, binding) { host ->
            configureWebViewHost(host, isMain = true)
        }
    }

    private fun configureWebViewHost(
        host: WebViewHost,
        isMain: Boolean,
        onTitleChanged: (String) -> Unit = {}
    ) {
        host.configure(prefs.incognitoMode) {
            if (isMain) loadWebUI() else host.webView.reload()
        }
        host.webView.webViewClient = SoAIWebViewClientFactory.create(this, host, pageOwner)
        host.webView.webChromeClient = SoAIWebChromeClientFactory.create(
            host,
            popupController,
            fileChooser,
            permissionMediator,
            onTitleChanged
        )
        if (isMain) {
            notificationBridgeController.attach(host.webView)
        }
    }

    private fun loadWebUI() {
        val loadGeneration = ++pageLoadGeneration
        WebViewDataCleaner.runWhenSessionDataReady {
            if (loadGeneration != pageLoadGeneration || isFinishing || isDestroyed || !::mainHost.isInitialized) return@runWhenSessionDataReady
            val serverUrl = prefs.serverUrl ?: return@runWhenSessionDataReady
            AndroidDeviceIdentityCookie.install(
                CookieManager.getInstance(),
                serverUrl,
                prefs.deviceIdentity()
            ) { installed ->
                if (loadGeneration != pageLoadGeneration || isFinishing || isDestroyed || !::mainHost.isInitialized) return@install
                if (!installed) {
                    mainHost.showError(getString(R.string.device_identity_cookie_failed))
                    return@install
                }
                mainHost.webView.loadUrl(launchRouter.consume(intent, serverUrl))
            }
        }
    }

    private fun recreateMainWebView(loadImmediately: Boolean = true) {
        pageLoadGeneration += 1L
        fileChooser.cancelAll()
        permissionMediator.cancelPending()
        notificationBridgeController.detach()
        trustController.cancelFor(mainHost.webView)
        mainHost.destroy()
        createMainWebView()
        if (loadImmediately) loadWebUI()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
