package com.ogl.game.analytics.core

import android.os.SystemClock

/** Time source. Durations use the monotonic clock so wall-clock changes cannot corrupt them. */
interface AnalyticsClock {
    fun elapsedRealtimeMillis(): Long
    fun currentTimeMillis(): Long
}

object SystemAnalyticsClock : AnalyticsClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
