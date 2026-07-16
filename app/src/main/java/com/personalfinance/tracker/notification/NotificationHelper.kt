package com.personalfinance.tracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.personalfinance.tracker.MainActivity

object NotificationHelper {
    const val CHANNEL_SMS = "sms_confirmation"
    const val CHANNEL_LOAN = "loan_reminders"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SMS, "Transaction confirmations", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "New transactions detected from bank SMS" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LOAN, "Loan due reminders", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Upcoming loan repayment reminders" }
            )
        }
    }

    fun notifyPendingTransaction(context: Context, pendingId: Long, amount: Double?, type: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_screen", "confirm_sms")
            putExtra("pending_id", pendingId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, pendingId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountText = amount?.let { "₹%.2f".format(it) } ?: "amount unclear"
        val notification = NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New transaction detected")
            .setContentText("$amountText ${type ?: ""} - tap to confirm")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(pendingId.toInt(), notification)
    }

    fun notifyLoanDue(context: Context, loanId: Long, loanName: String, dueInDays: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_screen", "loans")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (1000 + loanId).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (dueInDays <= 0) "$loanName is due today"
                    else "$loanName is due in $dueInDays day(s)"

        val notification = NotificationCompat.Builder(context, CHANNEL_LOAN)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Loan payment reminder")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify((1000 + loanId).toInt(), notification)
    }
}
