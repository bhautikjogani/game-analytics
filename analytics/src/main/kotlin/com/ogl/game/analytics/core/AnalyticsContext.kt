package com.ogl.game.analytics.core

import android.content.Context
import android.os.Build
import com.ogl.game.analytics.CommonParameter

/** Module-managed context automatically attached to events. */
data class AnalyticsContext(val userUuid: String, val country: String) {
    companion object { const val UNKNOWN_COUNTRY = "unknown" }
}

internal data class AppInfo(val appVersion: String?, val appBuild: Long?, val osVersion: String?) {

    fun toParameters(requested: Set<CommonParameter>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for (p in requested) {
            when (p) {
                CommonParameter.APP_VERSION -> appVersion?.let { out[AnalyticsKeys.APP_VERSION] = it }
                CommonParameter.APP_BUILD -> appBuild?.let { out[AnalyticsKeys.APP_BUILD] = it }
                CommonParameter.PLATFORM -> out[AnalyticsKeys.PLATFORM] = "android"
                CommonParameter.OS_VERSION -> osVersion?.let { out[AnalyticsKeys.OS_VERSION] = it }
            }
        }
        return out
    }

    companion object {
        @Suppress("DEPRECATION")
        fun from(context: Context): AppInfo = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            AppInfo(pi.versionName, build, Build.VERSION.RELEASE)
        } catch (t: Throwable) {
            AppInfo(null, null, Build.VERSION.RELEASE)
        }
    }
}
