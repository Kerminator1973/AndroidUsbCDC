package ru.dors.androidusbcdc

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for all persistent application preferences.
 * Eliminates the "USB_CDC_PREFS" magic string and duplicate getSharedPreferences()
 * calls scattered across multiple Activities.
 *
 * Key values intentionally match the original R.string resource values to remain
 * backward-compatible with preferences saved by previous app versions.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var useDSlipProtocol: Boolean
        get() = prefs.getBoolean(KEY_PROTOCOL, DEFAULT_USE_DSLIP)
        set(value) = prefs.edit().putBoolean(KEY_PROTOCOL, value).apply()

    var useDefaultSpeed: Boolean
        get() = prefs.getBoolean(KEY_SPEED, DEFAULT_USE_DEFAULT_SPEED)
        set(value) = prefs.edit().putBoolean(KEY_SPEED, value).apply()

    companion object {
        private const val PREFS_NAME             = "USB_CDC_PREFS"
        const val KEY_PROTOCOL                   = "protocol_type"
        const val KEY_SPEED                      = "speed_value"
        private const val DEFAULT_USE_DSLIP      = true   // DSlip
        private const val DEFAULT_USE_DEFAULT_SPEED = true   // 115 200 baud
    }
}
