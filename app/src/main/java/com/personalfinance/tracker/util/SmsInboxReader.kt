package com.personalfinance.tracker.util

import android.content.Context
import android.provider.Telephony
import com.personalfinance.tracker.sms.SmsParser

/**
 * Reads the device SMS inbox to find the most recent message from one of the
 * given sender IDs, then parses its amount. Used by the account "refresh"
 * action to load the current balance from the bank's last SMS.
 */
object SmsInboxReader {

    data class Result(val body: String?, val amount: Double?)

    fun lastSmsForSenders(context: Context, senderIds: List<String>): Result {
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.Inbox.BODY, Telephony.Sms.Inbox.ADDRESS)
        val sort = "${Telephony.Sms.Inbox.DATE} DESC"

        context.contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addrIdx) ?: continue
                if (senderIds.any { address.contains(it, ignoreCase = true) || it.contains(address, ignoreCase = true) }) {
                    val body = cursor.getString(bodyIdx)
                    // Prefer the remaining balance (مانده) for the account balance;
                    // fall back to the transaction amount if no balance is present.
                    val parsed = SmsParser.parse(body)
                    return Result(body, parsed.balanceAfter ?: parsed.amount)
                }
            }
        }
        return Result(null, null)
    }

    /**
     * A sender detected from the SMS inbox: its raw address (phone number / short
     * code), which is also what gets stored as the sender ID (it is what the inbox
     * actually uses to deliver messages). [displayName] mirrors [address] since the
     * app no longer reads contacts, so the UI always shows the real sender address.
     */
    data class DetectedSender(val address: String, val displayName: String?)

    /**
     * Returns the most recent distinct sender addresses from the inbox, newest first,
     * so the user can pick their bank's sender ID without typing it manually.
     * Senders already configured are excluded. The display name is the raw address
     * (contacts are not accessed).
     */
    fun recentSenders(context: Context, exclude: Set<String> = emptySet(), limit: Int = 30): List<DetectedSender> {
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.Inbox.ADDRESS)
        val sort = "${Telephony.Sms.Inbox.DATE} DESC"
        val out = LinkedHashSet<DetectedSender>()
        context.contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
            while (cursor.moveToNext() && out.size < limit) {
                val address = cursor.getString(addrIdx)?.trim() ?: continue
                if (address.isBlank()) continue
                if (exclude.any { address.equals(it, ignoreCase = true) || address.contains(it, ignoreCase = true) }) continue
                out.add(DetectedSender(address, address))
            }
        }
        return out.toList()
    }
    /**
     * A single bank SMS from the device inbox, with its parsed transaction details.
     */
    data class SmsMessage(
        val address: String,
        val body: String,
        val dateMillis: Long,
        val amount: Double?,   // in Toman (parsed)
        val type: com.personalfinance.tracker.data.TxType?
    )

    /**
     * Returns every inbox SMS from the given sender IDs, newest first, with the
     * amount/type parsed. Used by the per-account "Show SMS" view so the user can
     * pick a message to reconcile against their transactions.
     */
    fun allSmsForSenders(context: Context, senderIds: List<String>, limit: Int = 200): List<SmsMessage> {
        if (senderIds.isEmpty()) return emptyList()
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.ADDRESS,
            Telephony.Sms.Inbox.DATE
        )
        val sort = "${Telephony.Sms.Inbox.DATE} DESC"
        val out = mutableListOf<SmsMessage>()
        context.contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)
            while (cursor.moveToNext() && out.size < limit) {
                val address = cursor.getString(addrIdx) ?: continue
                if (!senderIds.any { address.contains(it, ignoreCase = true) || it.contains(address, ignoreCase = true) }) continue
                val body = cursor.getString(bodyIdx) ?: continue
                val date = cursor.getLong(dateIdx)
                val parsed = SmsParser.parse(body)
                out.add(SmsMessage(address, body, date, parsed.amount, parsed.type))
            }
        }
        return out
    }

    /**
     * Finds the inbox SMS for the given senders whose date matches [dateMillis]
     * (same device timestamp), or null if none. Used to re-load a selected SMS
     * when pre-filling the Add Transaction screen.
     */
    fun findSmsByDate(context: Context, senderIds: List<String>, dateMillis: Long): SmsMessage? {
        return allSmsForSenders(context, senderIds).firstOrNull { it.dateMillis == dateMillis }
    }
}
