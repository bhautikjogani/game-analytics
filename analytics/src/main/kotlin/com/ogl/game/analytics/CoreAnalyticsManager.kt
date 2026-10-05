package com.ogl.game.analytics

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.ogl.game.analytics.firebase.AnalyticsBackend
import com.ogl.game.analytics.firebase.FirebaseAnalyticsAdapter
import com.ogl.game.analytics.firebase.NoOpAnalyticsBackend
import java.util.Locale
import java.util.UUID

/**
 * A highly generic, stable, and clean Analytics Manager for apps and games.
 * Automatically handles UUID, Timestamps, Country, and Session Durations.
 */
class CoreAnalyticsManager private constructor(
    private val context: Context,
    private val backend: AnalyticsBackend
) {
    // Stores active sessions/timers to automatically calculate durations
    private val activeTimers = mutableMapOf<String, Long>()
    
    // Auto-generated UUID for the user/device.
    private var userUUID: String = UUID.randomUUID().toString()
    
    // Auto-detected country
    private val country: String = getCountryCode(context)

    companion object {
        private const val TAG = "CoreAnalytics"
        const val PARAM_UUID = "user_uuid"
        const val PARAM_TIMESTAMP = "timestamp"
        const val PARAM_DURATION = "duration_seconds"
        const val PARAM_COUNTRY = "country"

        @Volatile private var instance: CoreAnalyticsManager? = null

        fun initialize(context: Context, firebaseEnabled: Boolean = true): CoreAnalyticsManager {
            return instance ?: synchronized(this) {
                instance ?: CoreAnalyticsManager(
                    context.applicationContext,
                    if (firebaseEnabled) FirebaseAnalyticsAdapter(context.applicationContext) else NoOpAnalyticsBackend
                ).also { instance = it }
            }
        }

        fun getInstance(): CoreAnalyticsManager {
            return instance ?: throw IllegalStateException("CoreAnalyticsManager is not initialized. Call initialize(context) first.")
        }
    }

    init {
        // Automatically set the UUID and Country as global user properties in Firebase
        backend.setUserProperty(PARAM_UUID, userUUID)
        backend.setUserProperty(PARAM_COUNTRY, country)
    }

    /**
     * Standard event tracker. Auto-injects UUID, Timestamp, and Country.
     */
    fun track(eventName: String, params: Map<String, Any?> = emptyMap()) {
        val safeEventName = eventName.take(40).replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
        if (safeEventName.isBlank()) return

        val safeParams = mutableMapOf<String, Any>()
        
        // 1. Auto-inject Global Properties
        safeParams[PARAM_UUID] = userUUID
        safeParams[PARAM_TIMESTAMP] = System.currentTimeMillis()
        safeParams[PARAM_COUNTRY] = country

        // 2. Add and sanitize custom parameters
        params.forEach { (key, value) ->
            if (value == null) return@forEach
            if (value is String && value.isBlank()) return@forEach

            val safeKey = key.take(40).replace(Regex("[^a-zA-Z0-9_]"), "_")
            when (value) {
                is String -> safeParams[safeKey] = value.take(100)
                is Number -> {
                    if (value is Float || value is Double) safeParams[safeKey] = value.toDouble()
                    else safeParams[safeKey] = value.toLong()
                }
                is Boolean -> safeParams[safeKey] = if (value) 1L else 0L
            }
        }

        try {
            backend.logEvent(safeEventName, safeParams)
            Log.d(TAG, "Logged: $safeEventName -> $safeParams")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging event", e)
        }
    }

    /**
     * Starts a timer/session. Useful for "Start Game" or "Screen Open".
     * @param timerId A unique ID for this session (e.g., "game_match_1")
     */
    fun startTimerEvent(timerId: String, eventName: String, params: Map<String, Any?> = emptyMap()) {
        activeTimers[timerId] = System.currentTimeMillis()
        
        val enhancedParams = params.toMutableMap()
        enhancedParams["action"] = "start"
        
        track(eventName, enhancedParams)
    }

    /**
     * Ends a timer/session. Auto-calculates the duration since startTimerEvent was called.
     */
    fun stopTimerEvent(timerId: String, eventName: String, params: Map<String, Any?> = emptyMap()) {
        val enhancedParams = params.toMutableMap()
        enhancedParams["action"] = "stop"

        // Auto-calculate duration if the timer was started
        val startTime = activeTimers.remove(timerId)
        if (startTime != null) {
            val durationMs = System.currentTimeMillis() - startTime
            val durationSeconds = durationMs / 1000.0
            enhancedParams[PARAM_DURATION] = durationSeconds
        }

        track(eventName, enhancedParams)
    }

    /**
     * Helper for easy screen tracking
     */
    fun trackScreenView(screenName: String, screenClass: String = "Activity") {
        track(FirebaseAnalytics.Event.SCREEN_VIEW, mapOf(
            FirebaseAnalytics.Param.SCREEN_NAME to screenName,
            FirebaseAnalytics.Param.SCREEN_CLASS to screenClass
        ))
    }
    
    /**
     * Set a custom global user property
     */
    fun setUserProperty(name: String, value: String?) {
        val safeName = name.take(24)
        val safeValue = if (value.isNullOrBlank()) null else value.take(36)
        backend.setUserProperty(safeName, safeValue)
    }

    private fun getCountryCode(context: Context): String {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val networkCountry = telephonyManager?.networkCountryIso
            if (!networkCountry.isNullOrBlank()) {
                return networkCountry.uppercase(Locale.getDefault())
            }
        } catch (e: Exception) {
            // Ignore permission or context errors
        }
        return Locale.getDefault().country.uppercase(Locale.getDefault())
    }
}
