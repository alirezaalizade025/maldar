package com.personalfinance.tracker.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple app-wide settings backed by SharedPreferences. Currently holds the
 * daily reminder configuration (enabled + the hour of day it should fire).
 */
object Settings {

    private const val PREFS = "app_settings"
    private const val KEY_DAILY_ENABLED = "daily_reminder_enabled"
    private const val KEY_DAILY_HOUR = "daily_reminder_hour"

    private val prefs: SharedPreferences?
        get() = AppContextProvider.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var dailyReminderEnabled: Boolean
        get() = prefs?.getBoolean(KEY_DAILY_ENABLED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_DAILY_ENABLED, value)?.apply() }

    var dailyReminderHour: Int
        get() = prefs?.getInt(KEY_DAILY_HOUR, 21)?.coerceIn(0, 23) ?: 21
        set(value) { prefs?.edit()?.putInt(KEY_DAILY_HOUR, value.coerceIn(0, 23))?.apply() }
}
