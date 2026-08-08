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
    private const val KEY_DAILY_MINUTE = "daily_reminder_minute"
    private const val KEY_READ_SMS_FROM_NOTIFICATIONS = "read_sms_from_notifications"
    private const val KEY_LAST_SMS_CHECK = "last_sms_check_millis"
    private const val KEY_IGNORED_SMS = "ignored_sms"

    private val prefs: SharedPreferences?
        get() = AppContextProvider.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var dailyReminderEnabled: Boolean
        get() = prefs?.getBoolean(KEY_DAILY_ENABLED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_DAILY_ENABLED, value)?.apply() }

    var dailyReminderHour: Int
        get() = prefs?.getInt(KEY_DAILY_HOUR, 21)?.coerceIn(0, 23) ?: 21
        set(value) { prefs?.edit()?.putInt(KEY_DAILY_HOUR, value.coerceIn(0, 23))?.apply() }

    var dailyReminderMinute: Int
        get() = prefs?.getInt(KEY_DAILY_MINUTE, 0)?.coerceIn(0, 59) ?: 0
        set(value) { prefs?.edit()?.putInt(KEY_DAILY_MINUTE, value.coerceIn(0, 59))?.apply() }

    var readSmsFromNotificationsEnabled: Boolean
        get() = prefs?.getBoolean(KEY_READ_SMS_FROM_NOTIFICATIONS, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_READ_SMS_FROM_NOTIFICATIONS, value)?.apply() }

    // Timestamp (ms) of the last time the user reviewed/checked inbox SMS. Used by
    // the "unchecked SMS" flow: only messages newer than this are shown, and an
    // empty value (0) means "never checked" -> show nothing until a check runs.
    var lastSmsCheckMillis: Long
        get() = prefs?.getLong(KEY_LAST_SMS_CHECK, 0L) ?: 0L
        set(value) { prefs?.edit()?.putLong(KEY_LAST_SMS_CHECK, value)?.apply() }

    // Set of "address|dateMillis" keys for inbox SMS the user has explicitly
    // ignored. Ignored SMS are excluded whenever we derive an account's remaining
    // balance from the inbox (refresh / "last seen"), so a wrong message never
    // overwrites the real balance. They also stop re-appearing in the unchecked
    // SMS review.
    var ignoredSms: Set<String>
        get() = prefs?.getStringSet(KEY_IGNORED_SMS, emptySet()) ?: emptySet()
        set(value) { prefs?.edit()?.putStringSet(KEY_IGNORED_SMS, value.toMutableSet())?.apply() }

    fun addIgnoredSms(key: String) {
        ignoredSms = ignoredSms + key
    }
}
