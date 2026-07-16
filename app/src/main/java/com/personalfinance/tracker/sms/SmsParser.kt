package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.TxType
import java.util.regex.Pattern

/**
 * Parser for Iranian bank SMS. Amounts in bank SMS are in Rial, so they are
 * converted to Toman (Rial / 10) before being returned. It looks for:
 *  1. An amount (optionally preceded by a Rial/Toman marker like "ریال", "تومان", "Rls", "IRR")
 *  2. A debit/credit keyword (Persian or English) to decide the transaction type.
 *
 * This is intentionally permissive - false positives are expected, which is exactly
 * why every parsed SMS goes to a confirm screen instead of being saved directly.
 */
object SmsParser {

    data class ParseResult(
        val amount: Double?,   // in Toman
        val type: TxType?,
        val merchantOrNote: String?
    )

    // Matches amounts like: 1,234,500  ریال ۵۰۰۰۰۰ تومان  Rls 500
    private val amountPattern = Pattern.compile(
        "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:ریال|rls|IRR|تومان|toman)?",
        Pattern.CASE_INSENSITIVE
    )

    // Fallback: a bare number that looks like money near a debit/credit keyword
    private val bareAmountPattern = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")

    private val debitKeywords = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "purchase", "deducted", "sent",
        "برداشت", "خرید", "پرداخت", "کسر", "کم شد", "شارژ", "هزینه"
    )
    private val creditKeywords = listOf(
        "credited", "credit", "received", "deposited", "refund", "added",
        "واریز", "دریافت", "سپرده", "برگشت", "اضافه", "مانده"
    )

    // Common patterns for extracting a merchant name, e.g. "at AMAZON" or "خرید از دیجیکالا"
    private val merchantPattern = Pattern.compile(
        "(?:at|to|از)\\s+([\\p{L}\\p{N} &._-]{3,40})",
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
            val rial = matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
            // Bank SMS amounts are in Rial; convert to Toman (Rial / 10).
            return rial / 10.0
        }
        // fallback: first plausible bare number (used only if a currency marker is absent
        // but a debit/credit keyword IS present - handled by caller's confidence check)
        val bareMatcher = bareAmountPattern.matcher(message)
        if (bareMatcher.find()) {
            val rial = bareMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
            return rial / 10.0
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
