package com.personalfinance.tracker.sms

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.personalfinance.tracker.data.AppDatabase
import com.personalfinance.tracker.data.PendingSmsEntity
import com.personalfinance.tracker.data.PendingStatus
import com.personalfinance.tracker.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        // Skip our own notifications to avoid loops.
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val body = "$title\n$text\n$bigText".trim()

        if (body.isBlank()) return
        if (!SmsParser.looksLikeTransaction(body)) return

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { handleNotification(this@BankNotificationListenerService, sbn.packageName, body) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    private suspend fun handleNotification(context: Context, packageName: String, body: String) {
        val db = AppDatabase.getInstance(context)
        val watchedSenders = db.smsSenderDao().getAllOnce()

        // Match either by configured sender id appearing in the text, or by the
        // originating app package if the user added a sender for that bank.
        val matched = watchedSenders.firstOrNull { watched ->
            body.contains(watched.senderId, ignoreCase = true) ||
                watched.senderId.equals(packageName, ignoreCase = true)
        } ?: return

        val parsed = SmsParser.parse(body)

        val pending = PendingSmsEntity(
            rawMessage = body,
            sender = matched.senderId,
            parsedAmount = parsed.amount,
            parsedType = parsed.type,
            timestampMillis = System.currentTimeMillis(),
            bankAccountId = matched.bankAccountId,
            status = PendingStatus.PENDING
        )
        val id = db.pendingSmsDao().insert(pending)

        NotificationHelper.ensureChannels(context)
        NotificationHelper.notifyPendingTransaction(context, id, parsed.amount, parsed.type?.name)
    }
}
