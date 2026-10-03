package com.ogl.game.analytics.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import kotlin.collections.iterator

/** The ONLY class in the module that knows about Firebase. Exceptions are handled by the engine. */
internal class FirebaseAnalyticsAdapter(context: Context) : AnalyticsBackend {

    private val appContext = context.applicationContext
    private val firebase: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(appContext) }

    override fun logEvent(name: String, params: Map<String, Any>) {
        firebase.logEvent(name, params.toBundle())
    }

    override fun setUserProperty(name: String, value: String?) {
        firebase.setUserProperty(name, value)
    }

    override fun setUserId(id: String?) {
        firebase.setUserId(id)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        firebase.setAnalyticsCollectionEnabled(enabled)
    }

    private fun Map<String, Any>.toBundle(): Bundle {
        val bundle = Bundle()
        for ((key, value) in this) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                else -> Unit // validator guarantees this never happens
            }
        }
        return bundle
    }
}
