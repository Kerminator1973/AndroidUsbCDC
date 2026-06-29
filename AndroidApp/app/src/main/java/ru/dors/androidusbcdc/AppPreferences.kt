package ru.dors.androidusbcdc

import android.content.Context
import android.content.SharedPreferences

/**
 * Данный класс обеспечивает единый достоверный источник для всех постоянных настроек приложения.
 * Класс устраняет магическую строку "USB_CDC_PREFS" и дублирование функции getSharedPreferences()
 * в вызовах, разбросанных по нескольким классам.
 *
 * Значения ключей намеренно соответствуют исходным значениям ресурсов R.string, чтобы сохранить
 * обратную совместимость с настройками, сохраненными в предыдущих версиях приложения.
 */

class AppPreferences(private val context: Context) {

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
