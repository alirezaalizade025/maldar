package com.personalfinance.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalfinance.tracker.util.Settings
import com.personalfinance.tracker.worker.DailyReminderScheduler

/**
 * Re-schedules the daily reminder after a device reboot, since WorkManager
 * periodic/unique work does not always survive a restart on all OEMs.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && Settings.dailyReminderEnabled) {
            DailyReminderScheduler.scheduleNext(context)
        }
    }
}
