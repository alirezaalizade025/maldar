package com.personalfinance.tracker.sms

import android.content.Context
import androidx.room.withTransaction
import com.personalfinance.tracker.data.AppDatabase
import com.personalfinance.tracker.data.PendingSmsEntity
import com.personalfinance.tracker.notification.NotificationHelper
import com.personalfinance.tracker.util.Digits

class BankMessageProcessor(private val db: AppDatabase) {
    suspend fun capture(sender: String, body: String, timestampMillis: Long): PendingSmsEntity? = db.withTransaction {
        if (body.isBlank() || SmsParser.isPasswordMessage(body)) return@withTransaction null
        val matched = SmsAccountMatcher.match(sender, body, db.smsSenderDao().getAllOnce(), db.bankAccountDao().getAllOnce())
            ?: return@withTransaction null
        val accountId = matched.bankAccountId
        // shortcut: compare identical/contained text within five seconds; use bank reference IDs when available.
        val start = timestampMillis - 5_000
        val end = timestampMillis + 5_000
        val content = canonical(body)
        fun sameMessage(other: String): Boolean {
            val otherContent = canonical(other)
            return content == otherContent || (minOf(content.length, otherContent.length) >= 20 &&
                (content.contains(otherContent) || otherContent.contains(content)))
        }
        if (db.pendingSmsDao().getInWindow(accountId, start, end).any { sameMessage(it.rawMessage) } ||
            db.transactionDao().getSmsInWindow(accountId, start, end).any { it.rawSms?.let(::sameMessage) == true }) {
            return@withTransaction null
        }
        val parsed = SmsParser.parse(body)
        val pending = PendingSmsEntity(rawMessage = body, sender = sender, parsedAmount = parsed.amount,
            parsedType = parsed.type, parsedBalance = parsed.balanceAfter,
            timestampMillis = timestampMillis, bankAccountId = accountId)
        pending.copy(id = db.pendingSmsDao().insert(pending))
    }

    private fun canonical(body: String) = Digits.toEnglish(body).replace('ي', 'ی').replace('ك', 'ک')
        .replace(Regex("\\s+"), " ").trim()

    companion object {
        suspend fun receive(context: Context, sender: String, body: String, timestampMillis: Long) {
            val pending = BankMessageProcessor(AppDatabase.getInstance(context)).capture(sender, body, timestampMillis) ?: return
            NotificationHelper.ensureChannels(context)
            NotificationHelper.notifyPendingTransaction(context, pending.id, pending.parsedAmount, pending.parsedType?.name)
        }
    }
}
