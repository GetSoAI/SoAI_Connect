// Retains connection setup and its active probe for ui/ConnectionSetupViewModel.kt.
// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soai.android.network.ConnectPairingPayload
import com.soai.android.network.DiscoveryException
import com.soai.android.network.DiscoveryFailureReason
import com.soai.android.network.DiscoveryResult
import com.soai.android.network.DiscoveryService
import com.soai.android.network.HttpClientFactory
import com.soai.android.network.QrAutoConnectPolicy
import com.soai.android.network.QrConnectDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ConnectionSetup(
    val input: String = "",
    val state: ConnectionState = ConnectionState.IDLE,
    val result: DiscoveryResult? = null,
    val pairing: ConnectPairingPayload? = null,
    val error: DiscoveryException? = null,
    val instanceMismatch: Boolean = false,
    val autoConnect: Boolean = false
)

internal class ConnectionSetupViewModel : ViewModel() {
    private val discovery = lazy {
        DiscoveryService(HttpClientFactory.create(), HttpClientFactory.createDiscoveryTlsReader())
    }
    private val mutableSetup = MutableStateFlow(ConnectionSetup())
    val setup = mutableSetup.asStateFlow()
    private var probe: Job? = null
    private var initialized = false

    fun restore(input: String, pairing: ConnectPairingPayload?) {
        if (initialized) return
        initialized = true
        edit(input)
        if (pairing?.discoveryInput == input.trim()) {
            mutableSetup.value = mutableSetup.value.copy(pairing = pairing)
        }
    }

    fun edit(input: String) {
        if (input == mutableSetup.value.input) return
        probe?.cancel()
        mutableSetup.value = ConnectionSetup(
            input = input,
            state = if (input.isBlank()) ConnectionState.IDLE else ConnectionState.READY
        )
    }

    fun check(pairing: ConnectPairingPayload? = mutableSetup.value.pairing) {
        val current = mutableSetup.value
        val input = current.input.trim()
        if (input.isEmpty() || current.state == ConnectionState.CHECKING) return
        val checking = ConnectionSetup(current.input, ConnectionState.CHECKING, pairing = pairing)
        mutableSetup.value = checking
        probe = viewModelScope.launch {
            val outcome = try {
                val result = discovery.value.discover(input)
                when (pairing?.let { QrAutoConnectPolicy.decide(it, result) }) {
                    QrConnectDecision.INSTANCE_MISMATCH ->
                        checking.copy(state = ConnectionState.ERROR, instanceMismatch = true)
                    QrConnectDecision.REVIEW ->
                        checking.copy(state = ConnectionState.VERIFIED, result = result, pairing = null)
                    QrConnectDecision.AUTO_CONNECT ->
                        checking.copy(state = ConnectionState.VERIFIED, result = result, autoConnect = true)
                    null -> checking.copy(state = ConnectionState.VERIFIED, result = result)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: DiscoveryException) {
                checking.copy(state = ConnectionState.ERROR, error = exception)
            } catch (exception: Exception) {
                Log.w(TAG, "Unexpected discovery failure", exception)
                checking.copy(
                    state = ConnectionState.ERROR,
                    error = DiscoveryException(DiscoveryFailureReason.NOT_REACHABLE, input)
                )
            }
            if (mutableSetup.value === checking) mutableSetup.value = outcome
        }
    }

    fun takeVerified(): DiscoveryResult? {
        val current = mutableSetup.value
        val result = current.result ?: return null
        mutableSetup.value = ConnectionSetup(current.input, ConnectionState.READY)
        return result
    }

    override fun onCleared() {
        if (discovery.isInitialized()) discovery.value.close()
    }

    private companion object {
        const val TAG = "ConnectionSetup"
    }
}
