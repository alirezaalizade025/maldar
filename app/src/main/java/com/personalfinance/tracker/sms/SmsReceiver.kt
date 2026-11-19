package com.personalfinance.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
                BankMessageProcessor.receive(context, sender, fullBody, smsTimestamp)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.personalfinance.tracker.util.CrashLogger.log("SMS capture failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

}
