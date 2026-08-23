// SPDX-License-Identifier: MIT

package com.soai.android.ui

import android.app.Activity
import android.content.Intent

internal object AppNavigation {
    fun openConnectionAndClearTask(activity: Activity) {
        openAndClearTask(activity, ConnectionActivity::class.java)
    }

    fun openMainAndClearTask(activity: Activity) {
        openAndClearTask(activity, MainActivity::class.java)
    }

    fun openMainAndFinish(activity: Activity) {
        activity.startActivity(Intent(activity, MainActivity::class.java))
        activity.finish()
    }

    private fun openAndClearTask(activity: Activity, destination: Class<out Activity>) {
        val intent = Intent(activity, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}
