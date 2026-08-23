// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.soai.android.R
import com.soai.android.data.AppPreferences
import com.soai.android.databinding.ActivitySettingsBinding
import com.soai.android.network.WakeOnLanException
import com.soai.android.network.WakeOnLanField
import com.soai.android.network.WakeOnLanSender
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class WakeOnLanSettings(
    private val activity: SettingsActivity,
    private val binding: ActivitySettingsBinding,
    private val prefs: AppPreferences,
    private val showMessage: (String) -> Unit
) {
    fun initialize() {
        binding.wolMacInput.setText(prefs.wolMac.orEmpty())
        binding.wolBroadcastInput.setText(prefs.wolBroadcastAddress.orEmpty())
        binding.wolPortInput.setText(String.format(Locale.getDefault(), "%d", prefs.wolPort))
        binding.wolWakeButton.setOnClickListener { send() }
    }

    fun persist() {
        prefs.wolMac = binding.wolMacInput.text?.toString()?.trim()?.ifBlank { null }
        prefs.wolBroadcastAddress = binding.wolBroadcastInput.text?.toString()?.trim()?.ifBlank { null }
        parsePort(binding.wolPortInput.text?.toString())?.let { port -> prefs.wolPort = port }
    }

    private fun send() {
        binding.wolMacLayout.error = null
        binding.wolPortLayout.error = null
        val macAddress = binding.wolMacInput.text?.toString()?.trim().orEmpty()
        if (macAddress.isBlank()) {
            binding.wolMacLayout.error = activity.getString(R.string.wol_error_invalid_mac)
            return
        }
        val port = parsePort(binding.wolPortInput.text?.toString())
        if (port == null) {
            binding.wolPortLayout.error = activity.getString(R.string.wol_error_invalid_port)
            return
        }
        val broadcastAddress = binding.wolBroadcastInput.text?.toString()?.trim()?.ifBlank { null }
        persist()
        binding.wolWakeButton.isEnabled = false
        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    WakeOnLanSender.sendWakeSignal(macAddress, broadcastAddress, port)
                }
                showMessage(activity.getString(R.string.wol_success))
            } catch (exception: WakeOnLanException) {
                showFieldError(exception.field)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Unexpected Wake-on-LAN failure", exception)
                showMessage(
                    activity.getString(
                        R.string.wol_error_failed,
                        activity.getString(R.string.wol_error_invalid_broadcast)
                    )
                )
            } finally {
                binding.wolWakeButton.isEnabled = true
            }
        }
    }

    private fun showFieldError(field: WakeOnLanField) {
        when (field) {
            WakeOnLanField.MAC -> binding.wolMacLayout.error =
                activity.getString(R.string.wol_error_invalid_mac)
            WakeOnLanField.PORT -> binding.wolPortLayout.error =
                activity.getString(R.string.wol_error_invalid_port)
            WakeOnLanField.BROADCAST -> showMessage(
                activity.getString(
                    R.string.wol_error_failed,
                    activity.getString(R.string.wol_error_invalid_broadcast)
                )
            )
        }
    }

    private fun parsePort(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) return prefs.wolPort
        return value.toIntOrNull()?.takeIf { port -> port in 1..65535 }
    }

    private companion object {
        const val TAG = "WakeOnLanSettings"
    }
}
