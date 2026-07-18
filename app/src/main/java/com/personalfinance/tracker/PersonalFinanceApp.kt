package com.personalfinance.tracker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personalfinance.tracker.data.AppDatabase
import com.personalfinance.tracker.data.FinanceRepository
import com.personalfinance.tracker.notification.NotificationHelper
import com.personalfinance.tracker.util.AppContextProvider
import com.personalfinance.tracker.util.CrashLogger
import com.personalfinance.tracker.util.Settings
import com.personalfinance.tracker.worker.DailyReminderScheduler
import com.personalfinance.tracker.worker.LoanReminderWorker
import java.util.concurrent.TimeUnit

class PersonalFinanceApp : Application() {

    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        AppContextProvider.init(this)
        val db = AppDatabase.getInstance(this)
        repository = FinanceRepository(db)

        try {
            NotificationHelper.ensureChannels(this)
            scheduleLoanReminderWorker()
            if (Settings.dailyReminderEnabled) DailyReminderScheduler.scheduleNext(this)
        } catch (_: Exception) {
            // None of these are essential for the app to launch; never let a
            // scheduling/channel failure crash startup.
        }
    }

    private fun scheduleLoanReminderWorker() {
        val request = PeriodicWorkRequestBuilder<LoanReminderWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "loan_reminder_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
