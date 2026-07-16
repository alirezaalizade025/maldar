package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.TxType
import java.util.regex.Pattern

/**
 * Generic parser that tries to work across many Indian/international bank SMS formats
 * without needing a bank-specific template. It looks for:
 *  1. An amount, preceded by a currency marker (Rs, INR, ₹, $, etc.)
 *  2. A debit/credit keyword to decide the transaction type.
 *
 * This is intentionally permissive - false positives are expected, which is exactly
 * why every parsed SMS goes to a confirmation screen instead of being saved directly.
 */
object SmsParser {

    data class ParseResult(
        val amount: Double?,
        val type: TxType?,
        val merchantOrNote: String?
    )

    // Matches amounts like: Rs.1,234.50  INR 500  ₹99  $45.00
    private val amountPattern = Pattern.compile(
        "(?:rs\\.?|inr|₹|usd|\\$)\\s?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    // Fallback: a bare number that looks like money near a debit/credit keyword
    private val bareAmountPattern = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")

    private val debitKeywords = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "purchase", "deducted", "sent"
    )
    private val creditKeywords = listOf(
        "credited", "credit", "received", "deposited", "refund", "added"
    )

    // Common patterns for extracting a merchant name, e.g. "at AMAZON" or "to John Doe"
    private val merchantPattern = Pattern.compile(
        "(?:at|to)\\s+([A-Za-z0-9 &._-]{3,30})",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(message: String): ParseResult {
        val lower = message.lowercase()

        val amount = extractAmount(message)

        val type: TxType? = when {
            debitKeywords.any { lower.contains(it) } -> TxType.EXPENSE
            creditKeywords.any { lower.contains(it) } -> TxType.INCOME
            else -> null
        }

        val merchant = merchantPattern.matcher(message).let {
            if (it.find()) it.group(1)?.trim() else null
        }

        return ParseResult(amount, type, merchant)
    }

    private fun extractAmount(message: String): Double? {
        val matcher = amountPattern.matcher(message)
        if (matcher.find()) {
            return matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }
        // fallback: first plausible bare number (used only if a currency marker is absent
        // but a debit/credit keyword IS present - handled by caller's confidence check)
        val bareMatcher = bareAmountPattern.matcher(message)
        if (bareMatcher.find()) {
            return bareMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }
        return null
    }

    /**
     * Quick check used by the receiver to decide whether a message is even worth
     * surfacing to the user, so random SMS (OTPs, promos) don't spam the pending list.
     */
    fun looksLikeTransaction(message: String): Boolean {
        val lower = message.lowercase()
        val hasKeyword = (debitKeywords + creditKeywords).any { lower.contains(it) }
        val hasAmount = amountPattern.matcher(message).find()
        return hasKeyword && hasAmount
    }
}
