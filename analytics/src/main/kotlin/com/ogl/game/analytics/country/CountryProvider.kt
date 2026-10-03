package com.ogl.game.analytics.country

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/** Returns an ISO-3166 alpha-2 code (upper-case), or null when it cannot be determined. */
fun interface CountryProvider {
    fun resolveCountry(): String?
}

/**
 * Permission-free resolution: SIM country, then network country, then device locale region.
 * (Locale reflects language settings, so it is the weakest signal and used last.)
 */
class DeviceCountryProvider(context: Context) : CountryProvider {
    private val appContext = context.applicationContext

    override fun resolveCountry(): String? {
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = sequenceOf(
            { tm?.simCountryIso },
            { tm?.networkCountryIso },
            { Locale.getDefault().country },
        )
        for (supplier in candidates) {
            val code = try { supplier()?.trim()?.uppercase(Locale.ROOT) } catch (t: Throwable) { null }
            if (code != null && code.length == 2 && code.all { it in 'A'..'Z' }) return code
        }
        return null
    }
}
