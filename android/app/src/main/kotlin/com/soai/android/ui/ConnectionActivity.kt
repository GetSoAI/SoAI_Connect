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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.android.material.color.MaterialColors
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivityConnectionBinding
import com.soai.android.network.DiscoveryException
import com.soai.android.network.DiscoveryFailureReason
import com.soai.android.network.DiscoveryResult
import com.soai.android.network.DiscoveryService
import com.soai.android.network.HttpClientFactory
import com.soai.android.network.ServerCertificatePin
import com.soai.android.network.ServerOrigin
import com.soai.android.network.TlsStatus
import com.soai.android.web.ExternalNavigation
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class ConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionBinding
    private lateinit var prefs: AppPreferences
    private val discoveryServiceDelegate = lazy {
        DiscoveryService(
            HttpClientFactory.create(),
            HttpClientFactory.createDiscoveryTlsReader()
        )
    }
    private val discoveryService by discoveryServiceDelegate

    private var connectionState = ConnectionState.IDLE
    private var renderedState: ConnectionState? = null
    private var discoveryResult: DiscoveryResult? = null

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
        binding.serverUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            if (connectionState == ConnectionState.READY || connectionState == ConnectionState.ERROR) {
                startDiscovery()
            }
            true
        }
        setupWebsiteLink()
        updateUI()
    }

    private fun setupWebsiteLink() {
        binding.websiteLink.setOnClickListener {
            val url = getString(R.string.website_url)
            ExternalNavigation.openInExternalApp(this, url) {
                showError(getString(R.string.error_opening_link))
            }
        }
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
                updateUI()
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

    private fun startDiscovery() {
        val input = binding.serverUrlInput.text?.toString()?.trim()
        if (input.isNullOrEmpty()) {
            binding.serverUrlLayout.error = getString(R.string.error_empty_url)
            return
        }

        binding.serverUrlLayout.error = null
        connectionState = ConnectionState.CHECKING
        updateUI()

        lifecycleScope.launch {
            try {
                discoveryResult = discoveryService.discover(input)
                connectionState = ConnectionState.VERIFIED
                updateUI()
            } catch (exception: CancellationException) {
                throw exception
            } catch (e: DiscoveryException) {
                connectionState = ConnectionState.ERROR
                showError(describeDiscoveryFailure(e))
                updateUI()
            } catch (e: Exception) {
                connectionState = ConnectionState.ERROR
                Log.w(TAG, "Unexpected discovery failure", e)
                showError(getString(R.string.error_server_not_reachable, input))
                updateUI()
            }
        }
    }

    private fun describeDiscoveryFailure(exception: DiscoveryException): String {
        val detail = exception.detail.orEmpty()
        return when (exception.reason) {
            DiscoveryFailureReason.INVALID_ADDRESS -> getString(R.string.error_invalid_address)
            DiscoveryFailureReason.IPV6_ZONE_UNSUPPORTED ->
                getString(R.string.error_ipv6_zone_unsupported)
            DiscoveryFailureReason.INVALID_PORT -> getString(R.string.error_invalid_port, detail)
            DiscoveryFailureReason.NOT_REACHABLE ->
                getString(R.string.error_server_not_reachable, detail)
            DiscoveryFailureReason.TEMPORARILY_UNAVAILABLE ->
                getString(R.string.error_server_temporarily_unavailable, detail)
            DiscoveryFailureReason.NOT_FOUND -> getString(R.string.error_server_not_found, detail)
            DiscoveryFailureReason.AMBIGUOUS -> getString(R.string.error_multiple_servers, detail)
            DiscoveryFailureReason.NOT_SOAI_SERVER ->
                getString(R.string.error_not_soai_server, detail)
        }
    }

    private fun connect() {
        val result = discoveryResult ?: return
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

    private fun updateUI() {
        if (renderedState != connectionState) {
            TransitionManager.beginDelayedTransition(binding.root, MaterialFadeThrough())
            renderedState = connectionState
        }
        val hasText = !binding.serverUrlInput.text.isNullOrBlank()
        if (connectionState != ConnectionState.CHECKING) {
            binding.actionButton.contentDescription = null
        }
        binding.infoContainer.visibility = if (hasText) View.GONE else View.VISIBLE

        when (connectionState) {
            ConnectionState.IDLE -> {
                binding.actionButton.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true
            }
            ConnectionState.READY -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = true
                binding.actionButton.text = getString(R.string.check_button)
                binding.actionButton.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.blue_500)
                binding.progressBar.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true
            }
            ConnectionState.CHECKING -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = false
                binding.actionButton.text = null
                binding.actionButton.contentDescription = getString(R.string.checking)
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.visibility = View.GONE
                binding.serverUrlInput.isEnabled = false
            }
            ConnectionState.VERIFIED -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = true
                binding.actionButton.contentDescription = null
                binding.actionButton.text = getString(R.string.connect_button)
                binding.actionButton.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.green_500)
                binding.progressBar.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true

                discoveryResult?.let { result ->
                    binding.statusText.visibility = View.VISIBLE
                    val identityLabel = result.instanceName?.let { name ->
                        getString(R.string.server_identity_named, result.version, name)
                    } ?: getString(R.string.server_identity, result.version)
                    val statusText = if (result.tlsStatus == TlsStatus.UNTRUSTED) {
                        getString(
                            R.string.server_found_untrusted_tls_identity,
                            identityLabel,
                            result.serverUrl
                        )
                    } else {
                        getString(R.string.server_found_identity, identityLabel, result.serverUrl)
                    }
                    val fallbackText = if (result.fallbackActive) {
                        getString(
                            R.string.server_found_fallback,
                            statusText,
                            result.preferredPort,
                            result.port
                        )
                    } else {
                        statusText
                    }
                    binding.statusText.text = if (result.cleartextToPublicHost) {
                        getString(R.string.server_found_cleartext_warning, fallbackText)
                    } else {
                        fallbackText
                    }
                    val statusColor = if (result.fallbackActive || result.cleartextToPublicHost) {
                        R.color.amber_500
                    } else {
                        R.color.green_500
                    }
                    binding.statusText.setTextColor(ContextCompat.getColor(this, statusColor))
                }
            }
            ConnectionState.ERROR -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = true
                binding.actionButton.text = getString(R.string.retry_button)
                binding.actionButton.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.blue_500)
                binding.progressBar.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true
            }
        }
    }

    private fun navigateToMain() {
        AppNavigation.openMainAndFinish(this)
    }

    private fun showError(message: String) {
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
        const val TAG = "ConnectionActivity"
    }
}

private enum class ConnectionState {
    IDLE,
    READY,
    CHECKING,
    VERIFIED,
    ERROR
}
