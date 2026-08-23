// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.soai.android.BuildConfig
import com.soai.android.R
import com.soai.android.databinding.ActivityAboutBinding
import com.soai.android.web.ExternalNavigation

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ContentWindowInsets.applyBottom(binding.contentContainer)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.appVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        binding.websiteLink.setOnClickListener {
            val url = getString(R.string.website_url)
            ExternalNavigation.openInExternalApp(this, url) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_opening_link),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        binding.thirdPartyNoticesLink.setOnClickListener {
            startActivity(Intent(this, LicensesActivity::class.java))
        }
    }
}
