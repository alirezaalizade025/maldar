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
                    return Result(body, SmsParser.parse(body).amount)
                }
            }
        }
        return Result(null, null)
    }

    /**
     * Returns the most recent distinct sender addresses from the inbox, newest first,
     * so the user can pick their bank's sender ID without typing it manually.
     * Senders already configured are excluded.
     */
    fun recentSenders(context: Context, exclude: Set<String> = emptySet(), limit: Int = 30): List<String> {
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.Inbox.ADDRESS)
        val sort = "${Telephony.Sms.Inbox.DATE} DESC"
        val out = LinkedHashSet<String>()
        context.contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
            while (cursor.moveToNext() && out.size < limit) {
                val address = cursor.getString(addrIdx)?.trim() ?: continue
                if (address.isBlank()) continue
                if (exclude.any { address.equals(it, ignoreCase = true) || address.contains(it, ignoreCase = true) }) continue
                out.add(address)
            }
        }
        return out.toList()
    }
}
