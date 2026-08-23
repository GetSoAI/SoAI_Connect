// SPDX-License-Identifier: MIT

package com.soai.android.web

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.soai.android.R
import com.soai.android.ui.LifecycleDialogRegistry

object ExternalNavigation {

    private val AUTOMATIC_EXTERNAL_SCHEMES = setOf("mailto", "tel")

    fun openInExternalApp(activity: AppCompatActivity, uri: Uri, onFailure: () -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            activity.startActivity(intent)
        } catch (exception: Exception) {
            Log.w(TAG, "No external application could open ${uri.scheme.orEmpty()} link", exception)
            onFailure()
        }
    }

    fun openInExternalApp(activity: AppCompatActivity, url: String, onFailure: () -> Unit) {
        openInExternalApp(activity, url.toUri(), onFailure)
    }

    internal fun confirmAndOpenNonHttp(
        activity: AppCompatActivity,
        dialogs: LifecycleDialogRegistry,
        uri: Uri,
        onFailure: () -> Unit
    ) {
        val scheme = uri.scheme?.lowercase()
        if (scheme in AUTOMATIC_EXTERNAL_SCHEMES) {
            openInExternalApp(activity, uri, onFailure)
            return
        }

        dialogs.show(MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.open_external_app_title)
            .setMessage(activity.getString(R.string.open_external_app_message, uri.toString()))
            .setPositiveButton(R.string.open) { _, _ ->
                openInExternalApp(activity, uri, onFailure)
            }
            .setNegativeButton(R.string.cancel, null))
    }

    private const val TAG = "ExternalNavigation"
}
