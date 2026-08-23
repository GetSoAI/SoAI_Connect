// SPDX-License-Identifier: MIT

package com.soai.android

import android.app.Activity
import android.app.Application
import com.soai.android.data.AppPreferences
import com.soai.android.notifications.SoAINotificationChannels
import com.soai.android.notifications.SoAINotificationServiceController
import com.soai.android.web.WebViewDataCleaner
import java.util.concurrent.atomic.AtomicBoolean

class SoAIApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var startedActivityCount = 0
    private val incognitoInvalidated = AtomicBoolean(false)
    private val foreground = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
        SoAINotificationChannels.ensure(this)
        if (AppPreferences.getInstance(this).incognitoMode) {
            WebViewDataCleaner.clearSessionData()
        }
    }

    fun consumeIncognitoInvalidation(): Boolean {
        return incognitoInvalidated.getAndSet(false)
    }

    fun isAppForegrounded(): Boolean {
        return foreground.get()
    }

    private fun invalidateIncognitoData() {
        val prefs = AppPreferences.getInstance(this)
        if (!prefs.incognitoMode) return

        incognitoInvalidated.set(true)
        WebViewDataCleaner.clearSessionData()
        SoAINotificationServiceController.stop(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        if (foreground.compareAndSet(false, true)) {
            SoAINotificationServiceController.sync(this)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount -= 1
        if (startedActivityCount > 0) return
        if (activity.isChangingConfigurations) return
        foreground.set(false)
        invalidateIncognitoData()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        lateinit var instance: SoAIApplication
            private set
    }
}
