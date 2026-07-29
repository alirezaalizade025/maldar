package com.personalfinance.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalfinance.tracker.notification.NotificationHelper
import com.personalfinance.tracker.util.Settings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Fires the daily reminder notification at the user-chosen hour, then reschedules
 * itself for the same time the next day. If the daily reminder is disabled it
 * simply does nothing (the scheduler also cancels pending work in that case).
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Settings.dailyReminderEnabled) return Result.success()
        return try {
            NotificationHelper.ensureChannels(applicationContext)
            NotificationHelper.notifyDailyReminder(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            DailyReminderScheduler.scheduleNext(applicationContext, replaceExisting = false)
        }
    }

    companion object {
        // Milliseconds until the next occurrence of the given hour (0-23) today/tomorrow.
        fun delayUntil(hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}

object DailyReminderScheduler {

    private const val WORK_NAME = "daily_reminder"

    /** Enqueue (or reschedule) the daily reminder aligned to the saved hour. */
    fun scheduleNext(context: Context, replaceExisting: Boolean = true) {
        if (!Settings.dailyReminderEnabled) {
            cancel(context)
            return
        }
        val delay = DailyReminderWorker.delayUntil(
            Settings.dailyReminderHour,
            Settings.dailyReminderMinute
        )
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /** Cancel any pending daily reminder work. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
