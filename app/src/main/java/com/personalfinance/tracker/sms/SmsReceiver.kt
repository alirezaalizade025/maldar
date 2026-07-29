package com.personalfinance.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.personalfinance.tracker.data.AppDatabase
import com.personalfinance.tracker.data.PendingSmsEntity
import com.personalfinance.tracker.data.PendingStatus
import com.personalfinance.tracker.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Concatenate multi-part SMS
        val sender = messages[0].originatingAddress ?: return
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val smsTimestamp = messages.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

        // Do DB work off the main thread; goAsync() keeps the receiver alive long enough.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIncomingSms(context, sender, fullBody, smsTimestamp)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleIncomingSms(context: Context, sender: String, body: String, timestampMillis: Long) {
        val db = AppDatabase.getInstance(context)
        val watchedSenders = db.smsSenderDao().getAllOnce()
        val accounts = db.bankAccountDao().getAllOnce()

        // Match sender against the user's dynamically-added list.
        // Matching is loose (contains) since banks often send from short codes or
        // varying formats like "AD-HDFCBK", "HDFCBK", or a plain phone number.
        val matched = SmsAccountMatcher.match(sender, body, watchedSenders, accounts)
            ?: return

        // Capture every SMS from a watched sender. We no longer pre-filter on
        // keywords/amounts here: the user reviews each one manually, so a missed
        // keyword shouldn't silently drop a real bank SMS.
        val parsed = SmsParser.parse(body)

        val pending = PendingSmsEntity(
            rawMessage = body,
            sender = sender,
            parsedAmount = parsed.amount,
            parsedType = parsed.type,
            parsedBalance = parsed.balanceAfter,
            timestampMillis = timestampMillis,
            bankAccountId = matched.bankAccountId,
            status = PendingStatus.PENDING
        )
        val id = db.pendingSmsDao().insert(pending)

        NotificationHelper.ensureChannels(context)
        NotificationHelper.notifyPendingTransaction(
            context, id, parsed.amount, parsed.type?.name
        )
    }
}
