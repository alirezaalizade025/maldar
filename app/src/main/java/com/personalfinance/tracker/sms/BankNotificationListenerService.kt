package com.personalfinance.tracker.sms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.personalfinance.tracker.util.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import com.personalfinance.tracker.util.CrashLogger

/**
 * Play-Store-compatible alternative to the restricted [SmsReceiver].
 * A Notification Listener can read bank transaction *notifications* (which banks
 * post for every card/account event) without the restricted READ_SMS permission.
 * The user enables it in system settings (Settings > Notifications > Maldar).
 *
 * Like the SMS path, nothing is saved automatically: a "pending" entry is created
 * and the user confirms it on the Confirm screen.
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!Settings.readSmsFromNotificationsEnabled) return
        // Skip our own notifications to avoid loops.
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val message = bigText.ifBlank { text }
        val body = listOf(title, message).filter { it.isNotBlank() }.distinct().joinToString("\n")

        if (body.isBlank()) return
        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        scope.launch {
            try {
                BankMessageProcessor.receive(this@BankNotificationListenerService, sbn.packageName, body, postedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashLogger.log("Bank notification capture failed", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
