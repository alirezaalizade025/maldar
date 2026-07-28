package com.personalfinance.tracker.util

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Minimal Gregorian <-> Jalali (Persian/Shamsi) conversion. No external dependency.
 * Based on the standard algorithm (NCA, Behzad Farokhi / Roozbeh Pournader).
 */
object JalaliCalendar {

    // ASCII digits -> Persian digits so dates/times render in the Farsi font.
    private fun Int.toPersian(): String {
        val map = arrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return this.toString().map { if (it.isDigit()) map[it.digitToInt()] else it }.joinToString("")
    }

    private val persianMonths = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    data class Jalali(val year: Int, val month: Int, val day: Int)

    fun fromGregorian(calendar: Calendar): Jalali {
        val gy = calendar.get(Calendar.YEAR)
        val gm = calendar.get(Calendar.MONTH) + 1
        val gd = calendar.get(Calendar.DAY_OF_MONTH)
        val monthOffsets = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 -
            (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + monthOffsets[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Jalali(jy, jm, jd)
    }

    // Returns the exact local-time boundaries of a Jalali month.
    fun jalaliMonthRange(base: Calendar, offsetMonths: Int): Pair<Long, Long> {
        val current = fromGregorian(base)
        val absoluteMonth = current.year * 12 + current.month - 1 + offsetMonths
        val year = Math.floorDiv(absoluteMonth, 12)
        val month = Math.floorMod(absoluteMonth, 12) + 1
        val nextAbsoluteMonth = absoluteMonth + 1
        val nextYear = Math.floorDiv(nextAbsoluteMonth, 12)
        val nextMonth = Math.floorMod(nextAbsoluteMonth, 12) + 1
        val start = toGregorian(year, month, 1).timeInMillis
        val end = toGregorian(nextYear, nextMonth, 1).timeInMillis - 1
        return start to end
    }

    fun monthLabel(base: Calendar, offsetMonths: Int): String {
        val j = fromGregorian(base)
        var (y, m) = j.year to j.month
        var off = offsetMonths
        while (off > 0) { m++; if (m > 12) { m = 1; y++ }; off-- }
        while (off < 0) { m--; if (m < 1) { m = 12; y-- }; off++ }
        m = m.coerceIn(1, 12)
        return "${persianMonths[m - 1]} ${y.toPersian()}"
    }

    // e.g. "۱۲ تیر ۱۴۰۳"
    fun formatDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val j = fromGregorian(cal)
        return "${j.day.toPersian()} ${persianMonths[j.month - 1]} ${j.year.toPersian()}"
    }

    /**
     * Whole-calendar-day distance between now and [targetMillis].
     *
     * This compares local day-start boundaries (00:00) instead of raw millis,
     * so the value doesn't look "one day less" because of partial-day truncation.
     */
    fun daysUntil(targetMillis: Long): Long {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val targetStart = Calendar.getInstance().apply {
            timeInMillis = targetMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(targetStart - todayStart)
    }

    // e.g. "۱۲ تیر ۱۴۰۳، ۱۴:۳۰"
    fun formatDateTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val j = fromGregorian(cal)
        val hh = cal.get(Calendar.HOUR_OF_DAY)
        val mm = cal.get(Calendar.MINUTE)
        return "${j.day.toPersian()} ${persianMonths[j.month - 1]} ${j.year.toPersian()}، ${hh.toPersian()}:${mm.toPersian()}"
    }

    /**
     * Returns the millis of the next occurrence of [payDayOfMonth] (Jalali) at midnight.
     * If that day has already passed this Jalali month, returns the day in the next month.
     */
    fun nextDueDateMillis(payDayOfMonth: Int): Long {
        val now = Calendar.getInstance()
        val jNow = fromGregorian(now)
        val targetMonth = if (payDayOfMonth >= jNow.day) jNow.month else jNow.month + 1
        val (y, m) = if (targetMonth <= 12) jNow.year to targetMonth
        else jNow.year + 1 to 1
        val day = payDayOfMonth.coerceIn(1, daysInMonth(y, m))
        return toGregorian(y, m, day).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }

    // Number of days in a given Jalali month (m is 1-based, 12 = Esfand).
    private fun daysInMonth(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month < 12 -> 30
        else -> if (isLeap(year)) 30 else 29
    }

    private fun isLeap(jy: Int): Boolean =
        fromGregorian(toGregorianUnchecked(jy + 1, 1, 1).apply {
            add(Calendar.DAY_OF_MONTH, -1)
        }).day == 30

    // Public wrapper around the private converter so it can be unit-tested.
    fun toGregorianPublic(jy: Int, jm: Int, jd: Int): Calendar = toGregorian(jy, jm, jd)

    private fun toGregorian(jy: Int, jm: Int, jd: Int): Calendar =
        toGregorianUnchecked(jy, jm, jd)

    private fun toGregorianUnchecked(jy: Int, jm: Int, jd: Int): Calendar {
        val jy2 = jy + 1595
        var days = -355668 + 365 * jy2 + (jy2 / 33) * 8 +
            ((jy2 % 33 + 3) / 4) + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val monthLengths = intArrayOf(
            0, 31, if ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31
        )
        var gm = 1
        while (gm <= 12 && gd > monthLengths[gm]) {
            gd -= monthLengths[gm]
            gm++
        }
        return java.util.GregorianCalendar().apply {
            clear()
            set(gy, gm - 1, gd, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
