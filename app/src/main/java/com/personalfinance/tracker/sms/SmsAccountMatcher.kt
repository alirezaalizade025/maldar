package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.BankAccountEntity
import com.personalfinance.tracker.data.SmsSenderEntity
import com.personalfinance.tracker.util.Digits

object SmsAccountMatcher {
    private const val IDENTIFIER = "[0-9*•●xX]+(?:[ \\t./-]+[0-9*•●xX]+)*"
    private val labelledIdentifier = Regex(
        "(?:حساب|کارت|سپرده|account|card)\\s*(?:شماره\\s*)?[:：=]?\\s*($IDENTIFIER)",
        RegexOption.IGNORE_CASE
    )
    private val maskedIdentifier = Regex("(?<![0-9])([*•●xX]+[0-9*•●xX]*(?:[ \\t-]+[0-9*•●xX]+)*)")

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

        val suffixes = identifierSuffixes(body)
        val accountById = accounts.associateBy { it.id }
        val cardMatches = senderCandidates.filter { watched ->
            val last4 = normalize(accountById[watched.bankAccountId]?.accountLast4.orEmpty())
            last4.length == 4 && last4 in suffixes
        }
        if (cardMatches.isNotEmpty()) {
            return cardMatches.distinctBy { it.bankAccountId }.singleOrNull()
        }

        // A shared sender is ambiguous without a matching card suffix.
        if (senderCandidates.map { it.bankAccountId }.distinct().size > 1) return null
        val only = senderCandidates.first()
        val configuredLast4 = normalize(accountById[only.bankAccountId]?.accountLast4.orEmpty())
        return only.takeIf { configuredLast4.isEmpty() }
    }

    fun bodyMatchesLast4(body: String, accountLast4: String): Boolean {
        val last4 = normalize(accountLast4)
        return last4.isEmpty() || last4.length == 4 && last4 in identifierSuffixes(body)
    }

    private fun identifierSuffixes(body: String): Set<String> {
        val text = Digits.toEnglish(body).filterNot { Character.getType(it) == Character.FORMAT.toInt() }
        // Keep number boundaries and masks so amounts cannot become account identifiers.
        return (labelledIdentifier.findAll(text) + maskedIdentifier.findAll(text))
            .map { it.groupValues[1].filterNot { c -> c.isWhitespace() || c in ".-/" }.takeLast(4) }
            .filter { it.length == 4 && it.all(Char::isDigit) }
            .toSet()
    }

    private fun normalize(value: String): String =
        Digits.toEnglish(value).filter(Char::isDigit)
}
