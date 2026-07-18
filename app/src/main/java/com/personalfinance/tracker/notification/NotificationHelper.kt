package com.personalfinance.tracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.personalfinance.tracker.MainActivity
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.Money

object NotificationHelper {
    const val CHANNEL_SMS = "sms_confirmation"
    const val CHANNEL_LOAN = "loan_reminders"
    const val CHANNEL_DAILY = "daily_reminder"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SMS, "تایید تراکنش‌ها", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "تراکنش‌های جدید شناسایی‌شده از پیامک بانک" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LOAN, "یادآوری سررسید وام", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "یادآوری پرداخت اقساط وام" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DAILY, "یادآوری روزانه", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "یادآوری ثبت تراکنش‌های روزانه" }
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

        val amountText = amount?.let { Money.format2(it) + " " + AppStrings.moneyUnit } ?: AppStrings.amountUnclear
        val notification = NotificationCompat.Builder(context, CHANNEL_SMS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("تراکنش جدید شناسایی شد")
            .setContentText("$amountText ${type ?: ""} - برای تایید لمس کنید")
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

        val text = if (dueInDays <= 0) "$loanName امروز سررسید دارد"
                    else "$loanName تا $dueInDays روز دیگر سررسید دارد"

        val notification = NotificationCompat.Builder(context, CHANNEL_LOAN)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("یادآوری پرداخت وام")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify((1000 + loanId).toInt(), notification)
    }

    fun notifyDailyReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_screen", "add_transaction")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("یادآوری ثبت تراکنش")
            .setContentText("فراموش نکنید امروز تراکنش‌هایتان را ثبت کنید.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(2000, notification)
    }
}
