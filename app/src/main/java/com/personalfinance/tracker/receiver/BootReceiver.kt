package com.personalfinance.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personalfinance.tracker.util.Settings
import com.personalfinance.tracker.worker.DailyReminderScheduler
import com.personalfinance.tracker.worker.LoanReminderWorker
import java.util.concurrent.TimeUnit

/**
 * Re-schedules the daily and loan reminders after a device reboot, since WorkManager
 * periodic/unique work does not always survive a restart on all OEMs.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule daily reminder if enabled
            if (Settings.dailyReminderEnabled) {
                DailyReminderScheduler.scheduleNext(context)
            }
            // Reschedule loan reminder worker
            val loanRequest = PeriodicWorkRequestBuilder<LoanReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "loan_reminder_check",
                ExistingPeriodicWorkPolicy.KEEP,
                loanRequest
            )
        }
    }
}
