package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.TxType
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parser for Iranian bank SMS.
 *
 * In this app, incoming bank SMS amounts are treated as Rial and are always
 * sanitized to Toman via division by 10 before being returned.
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
    private val UNSIGNED_NUMBER = "[0-9۰-۹٠-٩][0-9۰-۹٠-٩,]*(?:\\.[0-9۰-۹٠-٩]{1,3})?"
    private val NUMBER = "([+-]?)(?<!\\d)($UNSIGNED_NUMBER)(?!\\d)"

    // Currency / unit markers that may follow an amount.
    private val RIAL_MARKER = "(?:ریال|rls|irr|ر\\.ا|ر ا)"
    private val TOMAN_MARKER = "(?:تومان|toman|ت\\.ا|ت ا)"
    private val UNIT = "(?:$RIAL_MARKER|$TOMAN_MARKER)"

    // 1) Amount introduced by مبلغ/amount/مقدار - the strongest transaction signal.
    private val keywordAmount = Pattern.compile(
        "(?:مبلغ|amount|مقدار)\\s*[:=]?\\s*$NUMBER\\s*$UNIT?",
        Pattern.CASE_INSENSITIVE
    )

    // 2) Some banks send only a signed amount on its own line. Both prefix
    // (-6,363,000) and suffix (44,000,000+) signs are used in real messages.
    // Keeping this line-anchored prevents the "-19" part of a date/time from
    // being interpreted as a transaction.
    private val signedLineAmount = Pattern.compile(
        "^[ \\t]*(?:([+-])[ \\t]*($UNSIGNED_NUMBER)|($UNSIGNED_NUMBER)[ \\t]*([+-]))[ \\t]*(?:$UNIT)?[ \\t]*$",
        Pattern.CASE_INSENSITIVE or Pattern.MULTILINE
    )

    // 3) Amount sitting next to a debit/credit keyword (برداشت ۱۲۰۰۰۰ / ۵۰۰۰۰۰ واریز شد).
    private val anchoredKwThenNum = Pattern.compile(
        "(?:${debitOrCreditPattern()})\\D{0,15}?$NUMBER",
        Pattern.CASE_INSENSITIVE
    )
    private val anchoredNumThenKw = Pattern.compile(
        "$NUMBER\\s*(?:${debitOrCreditPattern()})",
        Pattern.CASE_INSENSITIVE
    )

    // 4) A number immediately followed by an explicit unit marker (ریال/تومان).
    private val labelledAmount = Pattern.compile(
        "$NUMBER\\s*$UNIT",
        Pattern.CASE_INSENSITIVE
    )

    // 4b) Loan/installment payment SMS (e.g. Bank Melli): a "قسط" line carrying
    // the paid installment amount, optionally followed by a trailing '-' (debit).
    // Group 1 = digits, group 2 = optional trailing sign.
    private val installmentAmount = Pattern.compile(
        "(?:قسط|اقساط|بازپرداخت|قسط وام|قسط تسهیلات|پرداخت قسط)\\s*[:=]?\\s*($UNSIGNED_NUMBER)\\s*([+-])?",
        Pattern.CASE_INSENSITIVE
    )

    // 5) The remaining balance: a number following a balance keyword.
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
            lower.contains("برداشت") -> TxType.EXPENSE
            lower.contains("واریز") -> TxType.INCOME
            debitKeywords.any { lower.contains(it) } -> TxType.EXPENSE
            creditKeywords.any { lower.contains(it) } -> TxType.INCOME
            else -> null
        }

        val merchant = merchantPattern.matcher(message).let {
            if (it.find()) it.group(1)?.trim() else null
        }

        return ParseResult(amountResult?.value, type, balanceAfter, merchant)
    }

    /** Returns true for bank messages that contain a card password/OTP rather than a transaction. */
    fun isPasswordMessage(message: String): Boolean {
        val lower = message.lowercase()
        val hasPasswordKeyword = listOf(
            "رمز پویا", "رمز یکبار مصرف", "رمز یک بار مصرف", "رمز دوم", "رمز کارت", "رمز",
            "کد تایید", "کد تأیید", "otp", "one time password", "verification code",
            "password", "passcode"
        ).any { lower.contains(it) }
        val hasOtpCode = Regex("\\b\\d{5,8}\\b").find(message) != null
        return hasPasswordKeyword && hasOtpCode
    }

    private fun normalizeDigits(s: String): String =
        s.map { ch ->
            when (ch) {
                in '۰'..'۹' -> ((ch - '۰') + '0'.code).toChar()
                in '٠'..'٩' -> ((ch - '٠') + '0'.code).toChar()
                else -> ch
            }
        }.joinToString("")

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

    private data class AmountResult(val value: Double, val sign: Int)

    private fun isBalanceContext(message: String, index: Int): Boolean {
        val from = (index - 48).coerceAtLeast(0)
        val window = message.substring(from, index).lowercase()
        return balanceKeywords.any {
            val keyword = it.lowercase()
            val position = window.lastIndexOf(keyword)
            position >= 0 && window.substring(position + keyword.length).none(Char::isDigit)
        }
    }

    private fun extractSignedLineAmount(message: String): AmountResult? {
        val matcher = signedLineAmount.matcher(message)
        while (matcher.find()) {
            val raw = matcher.group(2) ?: matcher.group(3) ?: continue
            val rawStart = if (matcher.group(2) != null) matcher.start(2) else matcher.start(3)
            if (isBalanceContext(message, rawStart)) continue
            if (looksLikeCardNumber(raw)) continue
            val value = toDouble(raw) ?: continue
            val scaled = scale(value) ?: continue
            val signText = matcher.group(1) ?: matcher.group(4)
            return AmountResult(scaled, if (signText == "+") 1 else -1)
        }
        return null
    }

    /**
     * Extracts the TRANSACTION amount (the diff). It deliberately ignores numbers
     * that sit right after a balance keyword (مانده/موجودی), so the remaining
     * balance is never mistaken for the transaction amount.
     */
    private fun extractTransactionAmount(message: String): AmountResult? {
        fun build(matcher: Matcher): AmountResult? {
            val raw = matcher.group(2) ?: return null
            if (looksLikeCardNumber(raw)) return null
            val value = toDouble(raw) ?: return null
            val sign = when (matcher.group(1)) {
                "+" -> 1
                "-" -> -1
                else -> 0
            }
            return AmountResult(scale(value) ?: return null, sign)
        }

        // 1) مبلغ/amount/مقدار ... number
        keywordAmount.matcher(message).takeIf { it.find() }?.let { m ->
            if (!isBalanceContext(message, m.start(2))) return build(m)
        }

        // 1b) Loan/installment payment: قسط:<amount>[-]. Always an expense.
        installmentAmount.matcher(message).takeIf { it.find() }?.let { m ->
            val raw = m.group(1) ?: return@let null
            if (looksLikeCardNumber(raw)) return@let null
            val value = toDouble(raw) ?: return@let null
            val sign = when (m.group(2)) {
                "+" -> 1
                "-" -> -1
                else -> -1
            }
            return AmountResult(scale(value) ?: return@let null, sign)
        }

        // 2) A signed number occupying a complete line. This is an explicit
        // transaction signal even when the SMS has no debit/credit keyword.
        extractSignedLineAmount(message)?.let { return it }

        // 3) debit/credit keyword ... number  (or number ... keyword)
        for (pattern in listOf(anchoredKwThenNum, anchoredNumThenKw)) {
            pattern.matcher(message).takeIf { it.find() }?.let { m ->
                if (!isBalanceContext(message, m.start(2))) return build(m)
            }
        }

        // 4) number followed by a unit marker (ریال/تومان)
        labelledAmount.matcher(message).takeIf { it.find() }?.let { m ->
            if (!isBalanceContext(message, m.start(2))) return build(m)
        }

        // 5) Fallback: first bare number that is NOT the balance, as long as a
        //    transaction keyword exists somewhere in the message.
        if (debitKeywords.any { message.lowercase().contains(it) } ||
            creditKeywords.any { message.lowercase().contains(it) }) {
            val m = bareAmount.matcher(message)
            while (m.find()) {
                val raw = m.group(2) ?: continue
                if (looksLikeCardNumber(raw)) continue
                if (isBalanceContext(message, m.start(2))) continue
                val value = toDouble(raw) ?: continue
                val sign = when (m.group(1)) {
                    "+" -> 1
                    "-" -> -1
                    else -> 0
                }
                return AmountResult(scale(value) ?: continue, sign)
            }
        }
        return null
    }

    /**
     * Extracts the REMAINING BALANCE (مانده/موجودی ...) as Toman.
     * Prioritizes balance keywords in the last 1-2 lines of the message,
     * then falls back to searching the entire message.
     */
    private fun extractBalance(message: String): Double? {
        // Split into lines and prioritize the last 1-2 lines
        val lines = message.split("\n")
        val searchTexts = listOf(
            // Last 1-2 lines combined
            lines.takeLast(2).joinToString("\n"),
            // Full message as fallback
            message
        )

        for (searchText in searchTexts) {
            val m = balanceAmount.matcher(searchText)
            if (m.find()) {
                val raw = m.group(2) ?: continue
                if (looksLikeCardNumber(raw)) continue
                val value = toDouble(raw) ?: continue
                val scaled = scale(value)
                if (scaled != null) return scaled
            }
        }

        // If no balance keyword found, try to extract any number from the last 1-2 lines
        // that looks like a balance (large number)
        val lastLines = lines.takeLast(2).joinToString("\n")
        val m = bareAmount.matcher(lastLines)
        while (m.find()) {
            val raw = m.group(2) ?: continue
            if (looksLikeCardNumber(raw)) continue
            val value = toDouble(raw) ?: continue
            // Only consider numbers that are reasonably large (likely a balance)
            if (value >= 1000.0) {
                val scaled = scale(value)
                if (scaled != null) return scaled
            }
        }
        return null
    }

    // Sanitizes all incoming SMS numbers from Rial to Toman.
    private fun scale(value: Double): Double? {
        if (value <= 0) return null
        return value / 10.0
    }

    /**
     * Quick check used by the receiver to decide whether a message is even worth
     * surfacing to the user, so random SMS (OTPs, promos) don't spam the pending list.
     */
    fun looksLikeTransaction(message: String): Boolean {
        if (isPasswordMessage(message)) return false
        if (extractSignedLineAmount(message) != null) return true

        val lower = message.lowercase()
        val hasKeyword = (debitKeywords + creditKeywords).any { lower.contains(it) }
        val hasAmount = keywordAmount.matcher(message).find() ||
            anchoredKwThenNum.matcher(message).find() ||
            anchoredNumThenKw.matcher(message).find() ||
            labelledAmount.matcher(message).find()
        val hasInstallment = installmentAmount.matcher(message).find()
        return (hasKeyword && hasAmount) || hasInstallment
    }
}
