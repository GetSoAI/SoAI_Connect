// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.soai.android.R
import com.soai.android.databinding.ActivityLicensesBinding
import java.io.IOException

class LicensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLicensesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ContentWindowInsets.applyBottom(binding.contentContainer)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.noticesText.text = readNotices()
            ?: getString(R.string.licenses_unavailable)
    }

    private fun readNotices(): String? {
        return try {
            resources.openRawResource(R.raw.licenses).use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Third-party notices resource could not be read", exception)
            null
        }
    }

    private companion object {
        const val TAG = "LicensesActivity"
    }
}
