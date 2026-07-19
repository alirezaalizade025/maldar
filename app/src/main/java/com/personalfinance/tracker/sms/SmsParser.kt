package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.TxType
import java.util.regex.Pattern

/**
 * Parser for Iranian bank SMS. Bank SMS amounts are expressed in either Rial or
 * Toman; the parser detects the unit from the surrounding text and normalizes
 * everything to Toman before returning.
 *
 * It looks for:
 *  1. A labelled amount, i.e. a number that is immediately followed by a Rial /
 *     Toman marker (ریال / تومان / Rls / IRR / toman ...). This is the most
 *     reliable signal and avoids picking up card numbers, OTP codes, dates, etc.
 *  2. A keyword-anchored amount: a number that appears next to a debit/credit
 *     keyword (e.g. "مبلغ ۱۲۰۰۰۰ تومان برداشت شد", "واریز ۵۰۰۰۰۰").
 *  3. A debit/credit keyword to decide the transaction type.
 *
 * Because the parser is still permissive, every result is surfaced to a confirm
 * screen instead of being saved directly.
 */
object SmsParser {

    data class ParseResult(
        val amount: Double?,   // in Toman
        val type: TxType?,
        val merchantOrNote: String?
    )

    private val debitKeywords = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "purchase", "deducted", "sent", "transferred",
        "برداشت", "خرید", "پرداخت", "کسر", "کم شد", "شارژ", "هزینه", "انتقال", "ارسال"
    )
    private val creditKeywords = listOf(
        "credited", "credit", "received", "deposited", "refund", "added", "transfer in",
        "واریز", "دریافت", "سپرده", "برگشت", "اضافه", "مانده", "انتقال به حساب"
    )

    private fun debitOrCreditPattern(): String =
        "(?:" + (debitKeywords + creditKeywords).joinToString("|") + ")"

    // A number, possibly with Persian/Arabic digits, thousands separators, and
    // an optional decimal part. Captures the digits as group 1.
    private val NUMBER = "(?<!\\d)([0-9۰-۹][0-9۰-۹,]*(?:\\.[0-9۰-۹]{1,3})?)(?!\\d)"

    // Currency / unit markers that may follow an amount.
    private val RIAL_MARKER = "(?:ریال|rls|irr|ر\\.ا|ر ا)"
    private val TOMAN_MARKER = "(?:تومان|toman|ت\\.ا|ت ا)"

    // 1) Amount directly followed by an explicit unit marker (preferred).
    private val labelledAmount = Pattern.compile(
        "$NUMBER\\s*(?:$RIAL_MARKER|$TOMAN_MARKER)",
        Pattern.CASE_INSENSITIVE
    )

    // 2) Amount preceded by a "مبلغ"/"amount" keyword.
    private val keywordAmount = Pattern.compile(
        "(?:مبلغ|amount|مقدار)\\s*[:=]?\\s*$NUMBER\\s*(?:$RIAL_MARKER|$TOMAN_MARKER)?",
        Pattern.CASE_INSENSITIVE
    )

    // 3) Amount sitting next to a debit/credit keyword. Two explicit patterns so
    //    the amount is always capture group 1 regardless of keyword/number order
    //    (e.g. "واریز ۵۰۰۰۰۰" or "۵۰۰۰۰۰ خرید شد").
    private val anchoredKwThenNum = Pattern.compile(
        "(?:${debitOrCreditPattern()})\\D{0,15}?$NUMBER",
        Pattern.CASE_INSENSITIVE
    )
    private val anchoredNumThenKw = Pattern.compile(
        "$NUMBER\\s*(?:${debitOrCreditPattern()})",
        Pattern.CASE_INSENSITIVE
    )

    // Bare-number fallback: only used when no labelled/anchored amount exists.
    private val bareAmount = Pattern.compile(NUMBER)

    // Common patterns for extracting a merchant name, e.g. "at AMAZON" or "خرید از دیجیکالا".
    private val merchantPattern = Pattern.compile(
        "(?:at|to|از|خرید از|پرداخت به|در)\\s+([\\p{L}\\p{N} &._-]{3,40})",
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

    private fun normalizeDigits(s: String): String =
        s.map { ch -> if (ch in '۰'..'۹') (ch - '۰' + '0') else ch }.joinToString("")

    private fun toDouble(raw: String?): Double? {
        if (raw == null) return null
        return normalizeDigits(raw.replace(",", "")).toDoubleOrNull()
    }

    /**
     * Rejects numbers that are clearly not transaction amounts:
     *  - 16-digit card numbers (optionally space-separated),
     *  - any number with more than 14 significant digits (card/reference numbers).
     */
    private fun looksLikeCardNumber(raw: String): Boolean {
        val digits = normalizeDigits(raw).replace(",", "").replace(" ", "")
        return digits.length == 16 || digits.length > 14
    }

    private fun extractAmount(message: String): Double? {
        // Prefer an explicitly-labelled amount (with a unit marker).
        labelledAmount.matcher(message).takeIf { it.find() }?.let { m ->
            val raw = m.group(1) ?: return@let
            if (looksLikeCardNumber(raw)) return@let
            val value = toDouble(raw) ?: return@let
            val isRial = m.group(0).contains(Regex(RIAL_MARKER, RegexOption.IGNORE_CASE))
            return scale(value, isRial)
        }

        // Amount introduced by "مبلغ/amount".
        keywordAmount.matcher(message).takeIf { it.find() }?.let { m ->
            val raw = m.group(1) ?: return@let
            if (looksLikeCardNumber(raw)) return@let
            val value = toDouble(raw) ?: return@let
            val isRial = m.group(0).contains(Regex(RIAL_MARKER, RegexOption.IGNORE_CASE))
            return scale(value, isRial)
        }

        // Amount next to a debit/credit keyword.
        val anchoredMatcher = anchoredKwThenNum.matcher(message).takeIf { it.find() }
            ?: anchoredNumThenKw.matcher(message).takeIf { it.find() }
        if (anchoredMatcher != null) {
            val raw = anchoredMatcher.group(1)
            if (raw != null && !looksLikeCardNumber(raw)) {
                val value = toDouble(raw) ?: return null
                val isRial = anchoredMatcher.group(0).contains(Regex(RIAL_MARKER, RegexOption.IGNORE_CASE))
                return scale(value, isRial)
            }
        }

        // Last resort: a bare number, but only when a transaction keyword exists
        // so we don't pick up OTPs / promo codes. We assume Toman for bare numbers
        // since Rial amounts are almost always labelled.
        if (debitKeywords.any { message.lowercase().contains(it) } ||
            creditKeywords.any { message.lowercase().contains(it) }) {
            bareAmount.matcher(message).takeIf { it.find() }?.let { m ->
                val raw = m.group(1) ?: return null
                if (looksLikeCardNumber(raw)) return null
                return toDouble(raw)
            }
        }
        return null
    }

    /**
     * Scales [value] (the raw matched number) to Toman.
     * - If the matched text explicitly says Rial, divide by 10.
     * - Otherwise assume the number is already in Toman (most Iranian SMS label
     *   the amount in Toman, e.g. "۱۲۰۰۰۰ تومان").
     */
    private fun scale(value: Double, isRial: Boolean): Double? {
        if (value <= 0) return null
        return if (isRial) value / 10.0 else value
    }

    /**
     * Quick check used by the receiver to decide whether a message is even worth
     * surfacing to the user, so random SMS (OTPs, promos) don't spam the pending list.
     */
    fun looksLikeTransaction(message: String): Boolean {
        val lower = message.lowercase()
        val hasKeyword = (debitKeywords + creditKeywords).any { lower.contains(it) }
        val hasAmount = labelledAmount.matcher(message).find() ||
            keywordAmount.matcher(message).find() ||
            anchoredKwThenNum.matcher(message).find() ||
            anchoredNumThenKw.matcher(message).find()
        return hasKeyword && hasAmount
    }
}
