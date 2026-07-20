package com.personalfinance.tracker.util

import java.util.Calendar
import java.util.Locale

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

        // Gregorian days elapsed since 1 Farvardin 1 (22 Mar 622 CE), inclusive.
        // Computed with proleptic Gregorian day counting to avoid fragile formulas.
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val gregDay = gregorianOrdinal(gy, gm, gd)
        val epochDay = gregorianOrdinal(622, 3, 22)
        var days = gregDay - epochDay // days since start of Jalali year 1

        var jy = 1
        while (true) {
            val yearLen = if (isLeap(jy)) 366 else 365
            if (days < yearLen) break
            days -= yearLen
            jy++
        }

        var jm = 1
        while (true) {
            val monthLen = when {
                jm <= 6 -> 31
                jm < 12 -> 30
                else -> if (isLeap(jy)) 30 else 29
            }
            if (days < monthLen) break
            days -= monthLen
            jm++
        }

        val jd = days + 1
        return Jalali(jy, jm, jd)
    }

    // Days since the (proleptic) Gregorian epoch 0001-01-01.
    private fun gregorianOrdinal(y: Int, m: Int, d: Int): Int {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var days = 0
        for (yy in 1 until y) {
            days += if (gregorianLeap(yy)) 366 else 365
        }
        for (mm in 0 until m - 1) {
            days += gDaysInMonth[mm]
            if (mm == 1 && gregorianLeap(y)) days += 1 // February leap day
        }
        days += d - 1
        return days
    }

    private fun gregorianLeap(y: Int): Boolean =
        (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)

    // Returns the [start, end] millis for the Gregorian month containing `base`
    // shifted by `offsetMonths`. Stored transaction dates are Gregorian millis, so
    // the range MUST be computed on the Gregorian calendar (not via a Jalali
    // conversion round-trip, which drifted and made reports/charts read nothing).
    fun jalaliMonthRange(base: Calendar, offsetMonths: Int): Pair<Long, Long> {
        val cal = (Calendar.getInstance().apply {
            time = base.time
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, offsetMonths)
        })
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1
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

    private fun isLeap(jy: Int): Boolean {
        return (jy % 33 == 1 || jy % 33 == 5 || jy % 33 == 9 || jy % 33 == 13 ||
                jy % 33 == 17 || jy % 33 == 22 || jy % 33 == 26 || jy % 33 == 30)
    }

    // Public wrapper around the private converter so it can be unit-tested.
    fun toGregorianPublic(jy: Int, jm: Int, jd: Int): Calendar = toGregorian(jy, jm, jd)

    // Convert Jalali (y, m, d) to a Gregorian Calendar.
    // Robust approach: count days from the Jalali epoch (1/1/1 = Gregorian 622-03-22)
    // and add them to an anchor Calendar. Avoids fragile month-arithmetic bugs.
    private fun toGregorian(jy: Int, jm: Int, jd: Int): Calendar {
        var days = 0L
        for (y in 1 until jy) days += if (isLeap(y)) 366 else 365
        for (m in 1 until jm) {
            days += when {
                m <= 6 -> 31
                m < 12 -> 30
                else -> if (isLeap(jy)) 30 else 29
            }
        }
        days += (jd - 1)

        // Anchor MUST be a Gregorian calendar. Using Locale("fa", "IR") would return a
        // Jalali calendar instance on Android, which corrupts the whole conversion.
        val anchor = Calendar.getInstance().apply {
            set(622, Calendar.MARCH, 22, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        anchor.add(Calendar.DAY_OF_MONTH, days.toInt())
        return anchor
    }
}
