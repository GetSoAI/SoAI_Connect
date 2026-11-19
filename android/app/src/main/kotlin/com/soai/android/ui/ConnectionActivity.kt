// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.transition.TransitionManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.MaterialColors
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivityConnectionBinding
import com.soai.android.network.ConnectPairingPayload
import com.soai.android.network.ConnectPairingPayloadParser
import com.soai.android.network.PairingScan
import com.soai.android.network.ServerCertificatePin
import com.soai.android.network.ServerOrigin
import com.soai.android.web.ExternalNavigation
import kotlinx.coroutines.launch

class ConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionBinding
    private lateinit var prefs: AppPreferences
    private val connectionScreenRenderer by lazy { ConnectionScreenRenderer(this, binding) }
    private val connectionSetup by lazy { ViewModelProvider(this)[ConnectionSetupViewModel::class.java] }
    private val qrScanLauncher = ConnectionQrScanLauncher(this, ::applyScannedPairing, ::showScanError)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPreferences.getInstance(this)

        if (prefs.hasConnectedServer()) {
            navigateToMain()
            return
        }
        prefs.clearServerState()

        binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ContentWindowInsets.applyBottom(binding.contentContainer)

        setSupportActionBar(binding.toolbar)

        val pairing = savedInstanceState?.getString(STATE_PENDING_QR_PAIRING)?.let { uri ->
            (ConnectPairingPayloadParser.parse(uri) as? PairingScan.Valid)?.payload
        }
        connectionSetup.restore(savedInstanceState?.getString(STATE_INPUT).orEmpty(), pairing)
        binding.serverUrlInput.isSaveEnabled = false
        binding.serverUrlInput.setText(connectionSetup.setup.value.input)
        setupTextWatcher()
        setupActionButton()
        qrScanLauncher.bind(binding.serverUrlLayout)
        binding.serverUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            if (connectionSetup.setup.value.state in setOf(ConnectionState.READY, ConnectionState.ERROR)) {
                checkAddress()
            }
            true
        }
        setupWebsiteLink()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionSetup.setup.collect { setup ->
                    connectionScreenRenderer.render(setup.state, setup.result, qrScanLauncher.isAvailable)
                    val error = setup.error
                    if (setup.instanceMismatch) {
                        showError(getString(R.string.qr_error_instance_mismatch), notify = false)
                    } else if (error != null) {
                        showError(DiscoveryResultPresentation.describeFailure(this@ConnectionActivity, error), notify = false)
                    }
                    if (setup.autoConnect && connectionSetup.setup.value === setup) connect()
                }
            }
        }
    }

    private fun setupWebsiteLink() {
        binding.websiteLink.setOnClickListener {
            val url = getString(R.string.website_url)
            ExternalNavigation.openInExternalApp(this, url) {
                showError(getString(R.string.error_opening_link))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) {
            val setup = connectionSetup.setup.value
            outState.putString(STATE_INPUT, setup.input)
            setup.pairing?.let { pairing -> outState.putString(STATE_PENDING_QR_PAIRING, pairing.pairingUri) }
        }
        super.onSaveInstanceState(outState)
    }

    private fun setupTextWatcher() {
        binding.serverUrlInput.doAfterTextChanged { text ->
            connectionSetup.edit(text?.toString().orEmpty())
        }
    }

    private fun setupActionButton() {
        binding.actionButton.setOnClickListener {
            when (connectionSetup.setup.value.state) {
                ConnectionState.READY, ConnectionState.ERROR -> checkAddress()
                ConnectionState.VERIFIED -> connect()
                else -> {}
            }
        }
    }

    private fun checkAddress() {
        if (connectionSetup.setup.value.input.isBlank()) {
            binding.serverUrlLayout.error = getString(R.string.error_empty_url)
        } else {
            binding.serverUrlLayout.error = null
            connectionSetup.check()
        }
    }

    private fun applyScannedPairing(pairing: ConnectPairingPayload) {
        if (!::binding.isInitialized || connectionSetup.setup.value.state == ConnectionState.CHECKING) return
        binding.serverUrlInput.setText(pairing.discoveryInput)
        binding.serverUrlInput.setSelection(binding.serverUrlInput.length())
        connectionSetup.check(pairing)
    }

    private fun showScanError(message: String) {
        if (::binding.isInitialized) showError(message)
    }

    private fun connect() {
        val result = connectionSetup.takeVerified() ?: return
        if (!ServerCertificatePin.isApplicable(
                ServerOrigin.normalize(result.serverUrl),
                result.instanceId,
                prefs.trustedServerOrigin,
                prefs.trustedServerInstanceId
            )
        ) {
            prefs.clearTrustedServerCertificate()
        }
        prefs.saveConnectedServer(result.serverUrl, result.instanceId)
        navigateToMain()
    }

    private fun navigateToMain() {
        AppNavigation.openMainAndFinish(this)
    }

    private fun showError(message: String, notify: Boolean = true) {
        TransitionManager.endTransitions(binding.root)
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.statusText.setTextColor(MaterialColors.getColor(binding.statusText, android.R.attr.colorError))
        if (notify) Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_connection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private companion object {
        const val STATE_PENDING_QR_PAIRING = "pending_qr_pairing"
        const val STATE_INPUT = "connection_input"
    }
}
