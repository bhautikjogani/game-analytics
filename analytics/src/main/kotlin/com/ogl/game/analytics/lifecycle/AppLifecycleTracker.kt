package com.ogl.game.analytics.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Process-level foreground/background callbacks. Does NOT touch game sessions. */
internal class AppLifecycleTracker(
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit,
) : DefaultLifecycleObserver {

    fun register() {
        // ProcessLifecycleOwner observers must be added on the main thread.
        Handler(Looper.getMainLooper()).post {
            try { ProcessLifecycleOwner.get().lifecycle.addObserver(this) } catch (_: Throwable) { }
        }
    }

    override fun onStart(owner: LifecycleOwner) = onForeground()
    override fun onStop(owner: LifecycleOwner) = onBackground()
}
