package com.personalfinance.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalfinance.tracker.data.AppDatabase
import com.personalfinance.tracker.notification.NotificationHelper
import java.util.concurrent.TimeUnit

class LoanReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()

        val activeLoans = db.loanDao().getActiveLoans()
        for (loan in activeLoans) {
            val msUntilDue = loan.dueDateMillis - now
            val daysUntilDue = TimeUnit.MILLISECONDS.toDays(msUntilDue)

            // Notify once we're within the reminder window (and not already overdue by a lot)
            if (daysUntilDue in 0..loan.reminderDaysBefore.toLong()) {
                NotificationHelper.ensureChannels(applicationContext)
                NotificationHelper.notifyLoanDue(applicationContext, loan.id, loan.name, daysUntilDue)
            }
        }
        return Result.success()
    }
}
