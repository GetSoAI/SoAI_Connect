// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.transition.TransitionManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.MaterialColors
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivityConnectionBinding
import com.soai.android.network.ConnectPairingPayload
import com.soai.android.network.ConnectPairingPayloadParser
import com.soai.android.network.DiscoveryException
import com.soai.android.network.DiscoveryResult
import com.soai.android.network.DiscoveryService
import com.soai.android.network.HttpClientFactory
import com.soai.android.network.PairingScan
import com.soai.android.network.QrAutoConnectPolicy
import com.soai.android.network.QrConnectDecision
import com.soai.android.network.ServerCertificatePin
import com.soai.android.network.ServerOrigin
import com.soai.android.web.ExternalNavigation
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class ConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionBinding
    private lateinit var prefs: AppPreferences
    private val connectionScreenRenderer by lazy { ConnectionScreenRenderer(this, binding) }
    private val discoveryServiceDelegate = lazy {
        DiscoveryService(
            HttpClientFactory.create(),
            HttpClientFactory.createDiscoveryTlsReader()
        )
    }
    private val discoveryService by discoveryServiceDelegate

    private var connectionState = ConnectionState.IDLE
    private var discoveryResult: DiscoveryResult? = null
    private var pendingQrPairing: ConnectPairingPayload? = null
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

        setupTextWatcher()
        setupActionButton()
        qrScanLauncher.bind(binding.serverUrlLayout)
        binding.serverUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            if (connectionState == ConnectionState.READY || connectionState == ConnectionState.ERROR) {
                startDiscovery()
            }
            true
        }
        setupWebsiteLink()
        connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
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
        pendingQrPairing?.let { pairing -> outState.putString(STATE_PENDING_QR_PAIRING, pairing.pairingUri) }
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val pairingUri = savedInstanceState.getString(STATE_PENDING_QR_PAIRING)
        if (pairingUri != null) {
            when (val scan = ConnectPairingPayloadParser.parse(pairingUri)) {
                is PairingScan.Valid -> pendingQrPairing = scan.payload
                else -> {
                    binding.serverUrlInput.text = null
                    discoveryResult = null
                    connectionState = ConnectionState.IDLE
                    connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
                    showError(getString(R.string.error_empty_url))
                    return
                }
            }
        }
        discoveryResult = null
        connectionState = if (binding.serverUrlInput.text.isNullOrBlank()) {
            ConnectionState.IDLE
        } else {
            ConnectionState.READY
        }
        connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
    }

    override fun onDestroy() {
        if (discoveryServiceDelegate.isInitialized()) {
            discoveryService.close()
        }
        super.onDestroy()
    }

    private fun setupTextWatcher() {
        binding.serverUrlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                connectionState = if (text.isEmpty()) ConnectionState.IDLE else ConnectionState.READY
                discoveryResult = null
                pendingQrPairing = null
                connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
            }
        })
    }

    private fun setupActionButton() {
        binding.actionButton.setOnClickListener {
            when (connectionState) {
                ConnectionState.READY, ConnectionState.ERROR -> startDiscovery()
                ConnectionState.VERIFIED -> connect()
                else -> {}
            }
        }
    }

    private fun applyScannedPairing(pairing: ConnectPairingPayload) {
        if (!::binding.isInitialized || connectionState == ConnectionState.CHECKING) return
        binding.serverUrlInput.setText(pairing.discoveryInput)
        binding.serverUrlInput.setSelection(binding.serverUrlInput.length())
        pendingQrPairing = pairing
        startDiscovery()
    }

    private fun showScanError(message: String) {
        if (::binding.isInitialized) showError(message)
    }

    private fun startDiscovery() {
        val input = binding.serverUrlInput.text?.toString()?.trim()
        if (input.isNullOrEmpty()) {
            binding.serverUrlLayout.error = getString(R.string.error_empty_url)
            return
        }

        binding.serverUrlLayout.error = null
        connectionState = ConnectionState.CHECKING
        connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)

        lifecycleScope.launch {
            try {
                val result = discoveryService.discover(input)
                val pairing = pendingQrPairing
                val decision = pairing?.let { QrAutoConnectPolicy.decide(it, result) }
                if (decision == QrConnectDecision.INSTANCE_MISMATCH) {
                    connectionState = ConnectionState.ERROR
                    showError(getString(R.string.qr_error_instance_mismatch))
                    connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
                    return@launch
                }
                if (decision == QrConnectDecision.REVIEW) pendingQrPairing = null
                discoveryResult = result
                connectionState = ConnectionState.VERIFIED
                connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
                if (decision == QrConnectDecision.AUTO_CONNECT) {
                    lifecycle.withStarted { connect() }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (e: DiscoveryException) {
                connectionState = ConnectionState.ERROR
                showError(DiscoveryResultPresentation.describeFailure(this@ConnectionActivity, e))
                connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
            } catch (e: Exception) {
                connectionState = ConnectionState.ERROR
                Log.w(TAG, "Unexpected discovery failure", e)
                showError(getString(R.string.error_server_not_reachable, input))
                connectionScreenRenderer.render(connectionState, discoveryResult, qrScanLauncher.isAvailable)
            }
        }
    }

    private fun connect() {
        val result = discoveryResult ?: return
        pendingQrPairing = null
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

    private fun showError(message: String) {
        TransitionManager.endTransitions(binding.root)
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.statusText.setTextColor(MaterialColors.getColor(binding.statusText, android.R.attr.colorError))
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
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
        const val TAG = "ConnectionActivity"
    }
}
