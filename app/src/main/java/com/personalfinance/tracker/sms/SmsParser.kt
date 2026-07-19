package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.TxType
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parser for Iranian bank SMS. Bank SMS amounts are expressed in either Rial or
 * Toman; the parser detects the unit from the surrounding text and normalizes
 * everything to Toman before returning.
 *
 * A typical Iranian transaction SMS contains TWO numbers:
 *   - the transaction amount (the "diff"): preceded by برداشت/واریز/خرید/مبلغ or a +/- sign
 *   - the remaining balance (the "remained"): preceded by مانده/باقیمانده/موجودی
 *
 * These are extracted separately. The transaction amount is what gets turned
 * into a transaction; the remaining balance is surfaced so it can update the
 * account balance. The two must never be confused.
 *
 * Because the parser is still permissive, every result is surfaced to a confirm
 * screen instead of being saved directly.
 */
object SmsParser {

    data class ParseResult(
        val amount: Double?,   // transaction amount, in Toman
        val type: TxType?,
        val balanceAfter: Double?, // remaining balance (مانده), in Toman
        val merchantOrNote: String?
    )

    // Debit = money leaving the account (expense).
    private val debitKeywords = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "purchase", "deducted", "sent", "transferred",
        "برداشت", "خرید", "پرداخت", "کسر", "کم شد", "شارژ", "هزینه", "انتقال", "ارسال"
    )
    // Credit = money entering the account (income).
    private val creditKeywords = listOf(
        "credited", "credit", "received", "deposited", "refund", "added", "transfer in",
        "واریز", "دریافت", "سپرده", "برگشت", "اضافه", "انتقال به حساب"
    )
    // Keywords that introduce the REMAINING BALANCE, not a transaction amount.
    private val balanceKeywords = listOf(
        "مانده", "باقیمانده", "موجودی", "balance", "remaining"
    )

    private fun debitOrCreditPattern(): String =
        "(?:" + (debitKeywords + creditKeywords).joinToString("|") + ")"

    // A number, possibly with Persian/Arabic digits, thousands separators, an
    // optional decimal part, and an optional leading +/- sign. Group 1 = sign, group 2 = digits.
    private val NUMBER = "([+-]?)(?<!\\d)([0-9۰-۹][0-9۰-۹,]*(?:\\.[0-9۰-۹]{1,3})?)(?!\\d)"

    // Currency / unit markers that may follow an amount.
    private val RIAL_MARKER = "(?:ریال|rls|irr|ر\\.ا|ر ا)"
    private val TOMAN_MARKER = "(?:تومان|toman|ت\\.ا|ت ا)"
    private val UNIT = "(?:$RIAL_MARKER|$TOMAN_MARKER)"

    // 1) Amount introduced by مبلغ/amount/مقدار - the strongest transaction signal.
    private val keywordAmount = Pattern.compile(
        "(?:مبلغ|amount|مقدار)\\s*[:=]?\\s*$NUMBER\\s*$UNIT?",
        Pattern.CASE_INSENSITIVE
    )

    // 2) Amount sitting next to a debit/credit keyword (برداشت ۱۲۰۰۰۰ / ۵۰۰۰۰۰ واریز شد).
    private val anchoredKwThenNum = Pattern.compile(
        "(?:${debitOrCreditPattern()})\\D{0,15}?$NUMBER",
        Pattern.CASE_INSENSITIVE
    )
    private val anchoredNumThenKw = Pattern.compile(
        "$NUMBER\\s*(?:${debitOrCreditPattern()})",
        Pattern.CASE_INSENSITIVE
    )

    // 3) A number immediately followed by an explicit unit marker (ریال/تومان).
    private val labelledAmount = Pattern.compile(
        "$NUMBER\\s*$UNIT",
        Pattern.CASE_INSENSITIVE
    )

    // 4) The remaining balance: a number following a balance keyword.
    private val balanceAmount = Pattern.compile(
        "(?:${balanceKeywords.joinToString("|")})\\D{0,20}?$NUMBER\\s*$UNIT?",
        Pattern.CASE_INSENSITIVE
    )

    // Bare-number fallback.
    private val bareAmount = Pattern.compile(NUMBER)

    // Common patterns for extracting a merchant name, e.g. "at AMAZON" or "خرید از دیجیکالا".
    private val merchantPattern = Pattern.compile(
        "(?:at|to|از|خرید از|پرداخت به|در)\\s+([\\p{L}\\p{N} &._-]{3,40})",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(message: String): ParseResult {
        val lower = message.lowercase()

        val amountResult = extractTransactionAmount(message)
        val balanceAfter = extractBalance(message)

        val type: TxType? = when {
            // An explicit sign on the transaction amount wins.
            amountResult?.sign == 1 -> TxType.INCOME
            amountResult?.sign == -1 -> TxType.EXPENSE
            debitKeywords.any { lower.contains(it) } -> TxType.EXPENSE
            creditKeywords.any { lower.contains(it) } -> TxType.INCOME
            else -> null
        }

        val merchant = merchantPattern.matcher(message).let {
            if (it.find()) it.group(1)?.trim() else null
        }

        return ParseResult(amountResult?.value, type, balanceAfter, merchant)
    }

    private fun normalizeDigits(s: String): String =
        s.map { ch -> if (ch in '۰'..'۹') ((ch - '۰') + '0'.code).toChar() else ch }.joinToString("")

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

    private data class AmountResult(val value: Double, val isRial: Boolean, val sign: Int)

    /**
     * Extracts the TRANSACTION amount (the diff). It deliberately ignores numbers
     * that sit right after a balance keyword (مانده/موجودی), so the remaining
     * balance is never mistaken for the transaction amount.
     */
    private fun extractTransactionAmount(message: String): AmountResult? {
        // True if the text just before [index] contains a balance keyword.
        fun isBalanceContext(index: Int): Boolean {
            val from = (index - 16).coerceAtLeast(0)
            val window = message.substring(from, index)
            return balanceKeywords.any { window.contains(it) }
        }

        fun build(matcher: Matcher, message: String): AmountResult? {
            val raw = matcher.group(2) ?: return null
            if (looksLikeCardNumber(raw)) return null
            val value = toDouble(raw) ?: return null
            // The unit marker may sit right after the number (anchored patterns
            // don't capture it), so check both the match and the following text.
            val isRial = matcher.group(0).contains(Regex(RIAL_MARKER, RegexOption.IGNORE_CASE)) ||
                unitAfter(message, matcher.end(2))
            val sign = when (matcher.group(1)) {
                "+" -> 1
                "-" -> -1
                else -> 0
            }
            return AmountResult(scale(value, isRial) ?: return null, isRial, sign)
        }

        // 1) مبلغ/amount/مقدار ... number
        keywordAmount.matcher(message).takeIf { it.find() }?.let { m ->
            if (!isBalanceContext(m.start(2))) return build(m, message)
        }

        // 2) debit/credit keyword ... number  (or number ... keyword)
        for (pattern in listOf(anchoredKwThenNum, anchoredNumThenKw)) {
            pattern.matcher(message).takeIf { it.find() }?.let { m ->
                if (!isBalanceContext(m.start(2))) return build(m, message)
            }
        }

        // 3) number followed by a unit marker (ریال/تومان)
        labelledAmount.matcher(message).takeIf { it.find() }?.let { m ->
            if (!isBalanceContext(m.start(2))) return build(m, message)
        }

        // 4) Fallback: first bare number that is NOT the balance, as long as a
        //    transaction keyword exists somewhere in the message.
        if (debitKeywords.any { message.lowercase().contains(it) } ||
            creditKeywords.any { message.lowercase().contains(it) }) {
            val m = bareAmount.matcher(message)
            while (m.find()) {
                val raw = m.group(2) ?: continue
                if (looksLikeCardNumber(raw)) continue
                if (isBalanceContext(m.start(2))) continue
                val value = toDouble(raw) ?: continue
                val isRial = m.group(0).contains(Regex(RIAL_MARKER, RegexOption.IGNORE_CASE)) ||
                    unitAfter(message, m.end(2))
                val sign = when (m.group(1)) {
                    "+" -> 1
                    "-" -> -1
                    else -> 0
                }
                return AmountResult(scale(value, isRial) ?: continue, isRial, sign)
            }
        }
        return null
    }

    /**
     * Extracts the REMAINING BALANCE (مانده/موجودی ...) as Toman.
     */
    private fun extractBalance(message: String): Double? {
        val m = balanceAmount.matcher(message)
        if (m.find()) {
            val raw = m.group(2) ?: return null
            if (looksLikeCardNumber(raw)) return null
            val value = toDouble(raw) ?: return null
            val isRial = m.group(0).contains(Regex(RIAL_MARKER, RegexOption.IGNORE_CASE)) ||
                unitAfter(message, m.end(2))
            return scale(value, isRial)
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

    // True if a Rial marker appears in the few characters following [index].
    // The anchored patterns don't capture the unit marker, which often sits
    // right after the number, so we scan the following text too.
    private fun unitAfter(message: String, index: Int): Boolean {
        val window = message.substring(
            index.coerceAtMost(message.length),
            (index + 8).coerceAtMost(message.length)
        )
        return window.contains(Regex(RIAL_MARKER, RegexOption.IGNORE_CASE))
    }

    /**
     * Quick check used by the receiver to decide whether a message is even worth
     * surfacing to the user, so random SMS (OTPs, promos) don't spam the pending list.
     */
    fun looksLikeTransaction(message: String): Boolean {
        val lower = message.lowercase()
        val hasKeyword = (debitKeywords + creditKeywords).any { lower.contains(it) }
        val hasAmount = keywordAmount.matcher(message).find() ||
            anchoredKwThenNum.matcher(message).find() ||
            anchoredNumThenKw.matcher(message).find() ||
            labelledAmount.matcher(message).find()
        return hasKeyword && hasAmount
    }
}
