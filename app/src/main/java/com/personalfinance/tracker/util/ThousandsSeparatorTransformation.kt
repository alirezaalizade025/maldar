package com.personalfinance.tracker.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Groups the integer part of a number with a thousands separator as the user types
 * (e.g. "1234567" -> "۱٬۲۳۴٬۵۶۷" in the Persian locale). The decimal part (if any)
 * is left untouched. The underlying value stays digit-only.
 */
class ThousandsSeparatorTransformation(
    private val locale: Locale = Money.faLocale
) : VisualTransformation {

    private val grouping = DecimalFormatSymbols(locale).groupingSeparator

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val (intPart, decPart) = raw.splitOnDecimal()
        val grouped = intPart.reversed()
            .chunked(3)
            .joinToString(separator = grouping.toString())
            .reversed()

        val out = if (decPart == null) grouped else "$grouped.$decPart"
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val sepBefore = countSeparators(intPart.take(offset).length)
                return (offset + sepBefore).coerceAtMost(out.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                return (offset - countSeparators(offset)).coerceAtLeast(0).coerceAtMost(raw.length)
            }
            private fun countSeparators(len: Int): Int = (len - 1).coerceAtLeast(0) / 3
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }

    private fun String.splitOnDecimal(): Pair<String, String?> {
        val dot = indexOf('.')
        if (dot < 0) return this to null
        return substring(0, dot) to substring(dot + 1)
    }
}
