package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.BankAccountEntity
import com.personalfinance.tracker.data.SmsSenderEntity
import com.personalfinance.tracker.util.Digits

object SmsAccountMatcher {
    fun match(
        sender: String,
        body: String,
        watchedSenders: List<SmsSenderEntity>,
        accounts: List<BankAccountEntity>
    ): SmsSenderEntity? {
        val senderCandidates = watchedSenders.filter {
            sender.contains(it.senderId, ignoreCase = true) ||
                it.senderId.contains(sender, ignoreCase = true) ||
                body.contains(it.senderId, ignoreCase = true)
        }
        if (senderCandidates.isEmpty()) return null

        val normalizedBody = normalize(body)
        val accountById = accounts.associateBy { it.id }
        val cardMatches = senderCandidates.filter { watched ->
            val last4 = normalize(accountById[watched.bankAccountId]?.accountLast4.orEmpty())
            last4.length == 4 && normalizedBody.contains(last4)
        }
        if (cardMatches.isNotEmpty()) return cardMatches.first()

        // A shared sender is ambiguous without a matching card suffix.
        if (senderCandidates.map { it.bankAccountId }.distinct().size > 1) return null
        val only = senderCandidates.first()
        val configuredLast4 = normalize(accountById[only.bankAccountId]?.accountLast4.orEmpty())
        return only.takeIf { configuredLast4.isEmpty() }
    }

    fun bodyMatchesLast4(body: String, accountLast4: String): Boolean {
        val last4 = normalize(accountLast4)
        return last4.isEmpty() || last4.length == 4 && normalize(body).contains(last4)
    }

    private fun normalize(value: String): String =
        Digits.toEnglish(value).filter(Char::isDigit)
}
