// INTEGRATION EXAMPLE ONLY.
package com.example.dominoes

import android.app.Application
import com.ogl.game.analytics.AnalyticsConfig
import com.ogl.game.analytics.AnalyticsManager

class MyApp : Application() {
    lateinit var analytics: AnalyticsManager
        private set

    override fun onCreate() {
        super.onCreate()
        analytics = AnalyticsManager.initialize(
            this,
            AnalyticsConfig(
                analyticsEnabled = false, // stay off until your own consent flow says yes
            ),
        )
        // if (myConsentStore.analyticsGranted) analytics.setAnalyticsConsent(true)
    }
}
