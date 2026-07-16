package com.personalfinance.tracker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personalfinance.tracker.data.AppDatabase
import com.personalfinance.tracker.data.FinanceRepository
import com.personalfinance.tracker.notification.NotificationHelper
import com.personalfinance.tracker.util.CrashLogger
import com.personalfinance.tracker.worker.LoanReminderWorker
import java.util.concurrent.TimeUnit

class PersonalFinanceApp : Application() {

    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        val db = AppDatabase.getInstance(this)
        repository = FinanceRepository(db)

        NotificationHelper.ensureChannels(this)
        scheduleLoanReminderWorker()
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
