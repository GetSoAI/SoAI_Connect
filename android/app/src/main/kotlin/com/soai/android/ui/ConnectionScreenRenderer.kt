// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.content.Context
import android.transition.TransitionManager
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.soai.android.R
import com.soai.android.databinding.ActivityConnectionBinding
import com.soai.android.network.DiscoveryResult

internal class ConnectionScreenRenderer(
    private val context: Context,
    private val binding: ActivityConnectionBinding
) {
    private var renderedState: ConnectionState? = null

    fun render(state: ConnectionState, discoveryResult: DiscoveryResult?, qrScannerAvailable: Boolean) {
        if (renderedState != state) {
            TransitionManager.endTransitions(binding.root)
            TransitionManager.beginDelayedTransition(binding.root, MaterialFadeThrough())
            renderedState = state
        }
        val hasText = !binding.serverUrlInput.text.isNullOrBlank()
        if (state != ConnectionState.CHECKING) binding.actionButton.contentDescription = null
        binding.infoContainer.visibility = if (hasText) View.GONE else View.VISIBLE
        binding.serverUrlLayout.isEndIconVisible = state != ConnectionState.CHECKING && qrScannerAvailable

        when (state) {
            ConnectionState.IDLE -> {
                binding.actionButton.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true
            }
            ConnectionState.READY -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = true
                binding.actionButton.text = context.getString(R.string.check_button)
                binding.actionButton.backgroundTintList = ContextCompat.getColorStateList(context, R.color.blue_500)
                binding.progressBar.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true
            }
            ConnectionState.CHECKING -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = false
                binding.actionButton.text = null
                binding.actionButton.contentDescription = context.getString(R.string.checking)
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.visibility = View.GONE
                binding.serverUrlInput.isEnabled = false
            }
            ConnectionState.VERIFIED -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = true
                binding.actionButton.contentDescription = null
                binding.actionButton.text = context.getString(R.string.connect_button)
                binding.actionButton.backgroundTintList = ContextCompat.getColorStateList(context, R.color.green_500)
                binding.progressBar.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true
                discoveryResult?.let { result ->
                    val status = DiscoveryResultPresentation.describeVerified(context, result)
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = status.text
                    binding.statusText.setTextColor(ContextCompat.getColor(context, status.colorRes))
                }
            }
            ConnectionState.ERROR -> {
                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.isEnabled = true
                binding.actionButton.text = context.getString(R.string.retry_button)
                binding.actionButton.backgroundTintList = ContextCompat.getColorStateList(context, R.color.blue_500)
                binding.progressBar.visibility = View.GONE
                binding.serverUrlInput.isEnabled = true
            }
        }
    }
}

internal enum class ConnectionState {
    IDLE,
    READY,
    CHECKING,
    VERIFIED,
    ERROR
}
