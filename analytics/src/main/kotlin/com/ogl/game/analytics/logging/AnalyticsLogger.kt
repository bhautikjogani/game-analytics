package com.ogl.game.analytics.logging

import android.util.Log
import com.ogl.game.analytics.core.AnalyticsKeys
import kotlin.collections.iterator

fun interface LogSink {
    fun log(priority: Int, tag: String, message: String, throwable: Throwable?)
}

object AndroidLogSink : LogSink {
    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        try {
            if (throwable != null) Log.println(priority, tag, message + "\n" + Log.getStackTraceString(throwable))
            else Log.println(priority, tag, message)
        } catch (_: Throwable) { /* logging must never throw */ }
    }
}

/** Debug-only logger. When disabled it prints nothing at all (suitable for release builds). */
class AnalyticsLogger(
    private val enabled: Boolean,
    private val sink: LogSink = AndroidLogSink,
) {
    fun event(name: String, params: Map<String, Any>) {
        if (!enabled) return
        val sb = StringBuilder("ANALYTICS EVENT\n------------------------\nevent: ").append(name)
        for ((k, v) in params) sb.append('\n').append(k).append(": ").append(display(k, v))
        sb.append("\n------------------------")
        sink.log(Log.DEBUG, TAG, sb.toString(), null)
    }

    fun debug(message: String) { if (enabled) sink.log(Log.DEBUG, TAG, message, null) }
    fun warn(message: String) { if (enabled) sink.log(Log.WARN, TAG, message, null) }
    fun error(message: String, t: Throwable? = null) { if (enabled) sink.log(Log.ERROR, TAG, message, t) }

    // The UUID is anonymous, but there is still no reason to print it in full.
    private fun display(key: String, value: Any): String =
        if (key == AnalyticsKeys.USER_UUID) value.toString().take(8) + "…" else value.toString()

    private companion object { const val TAG = "Analytics" }
}
