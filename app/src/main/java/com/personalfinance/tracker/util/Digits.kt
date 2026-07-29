package com.personalfinance.tracker.util

/**
 * Converts ASCII digits (0-9) in a string/number to Persian digits (۰-۹) so all
 * numeric UI renders in the Farsi font. Android's String.format with the fa-IR
 * locale only sets the grouping separator and can still emit ASCII digits.
 */
object Digits {
    private val fa = arrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersian(input: Any): String {
        val s = input.toString()
        return s.map { if (it.isDigit()) fa[it.digitToInt()] else it }.joinToString("")
    }

    fun toEnglish(input: Any): String = input.toString().map { c ->
        when (c) {
            in '۰'..'۹' -> ('0'.code + (c.code - '۰'.code)).toChar()
            in '٠'..'٩' -> ('0'.code + (c.code - '٠'.code)).toChar()
            else -> c
        }
    }.joinToString("")
}

/**
 * Formats a Farsi template (with %d / %s / %,f placeholders) using the US locale
 * (so grouping separators are deterministic) and then converts every ASCII digit
 * in the result to Persian, guaranteeing Persian numerals in the UI.
 */
fun String.fa(vararg args: Any): String {
    return Digits.toPersian(this.format(java.util.Locale.US, *args))
}
