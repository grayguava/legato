package com.grayguava.legato.library

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Which country the listener is in, for suggestions that start from its
 * charts: its mobile network or SIM; else the region of its language. The
 * language comes last because many phones run in English (US) far from the US.
 */
public object UserCountry {
    /** Two-letter country code, upper case, or null when nothing names one. */
    public fun detect(context: Context): String? = pick(network(context), Locale.getDefault().country)

    /** The first of these that is a country code. */
    internal fun pick(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { it?.trim()?.uppercase(Locale.ROOT)?.takeIf { c -> c.length == 2 && c.all(Char::isLetter) } }

    private fun network(context: Context): String? = runCatching {
        val phone = context.getSystemService(TelephonyManager::class.java) ?: return null
        phone.networkCountryIso?.takeIf { it.length == 2 } ?: phone.simCountryIso?.takeIf { it.length == 2 }
    }.getOrNull()
}
