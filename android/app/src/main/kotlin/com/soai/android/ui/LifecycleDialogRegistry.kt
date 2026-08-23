// SPDX-License-Identifier: MIT

package com.soai.android.ui

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal class LifecycleDialogRegistry(activity: AppCompatActivity) : DefaultLifecycleObserver {
    private val dialogs = mutableSetOf<AlertDialog>()

    init {
        activity.lifecycle.addObserver(this)
    }

    fun show(builder: MaterialAlertDialogBuilder): AlertDialog {
        val dialog = builder.create()
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
        return dialog
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dialogs.toList().forEach { dialog -> dialog.dismiss() }
        dialogs.clear()
        owner.lifecycle.removeObserver(this)
    }
}
