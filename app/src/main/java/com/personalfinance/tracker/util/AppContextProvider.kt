package com.personalfinance.tracker.util

import android.content.Context

/**
 * Holds the application context so utilities that need SharedPreferences
 * (e.g. UpdateChecker's "skip version") can access it without passing a Context
 * through every call site.
 */
object AppContextProvider {
    var appContext: Context? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
