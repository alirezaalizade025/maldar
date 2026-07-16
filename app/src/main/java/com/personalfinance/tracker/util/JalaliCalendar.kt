package com.personalfinance.tracker.util

import java.util.Calendar
import java.util.Locale

/**
 * Minimal Gregorian <-> Jalali (Persian/Shamsi) conversion. No external dependency.
 * Based on the standard algorithm (NCA, Behzad Farokhi / Roozbeh Pournader).
 */
object JalaliCalendar {

    private val persianMonths = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    data class Jalali(val year: Int, val month: Int, val day: Int)

    fun fromGregorian(calendar: Calendar): Jalali {
        var gy = calendar.get(Calendar.YEAR)
        val gm = calendar.get(Calendar.MONTH) + 1
        val gd = calendar.get(Calendar.DAY_OF_MONTH)

        var jy: Int
        if (gy > 1600) {
            jy = 979
            gy -= 1600
        } else {
            jy = 0
            gy -= 621
        }

        val gd2 = if (gm > 2) gd + (if (isLeap(gy + 1)) 30 else 29) + (gm - 3) * 30 + dayOfYear(gm - 1)
        else gd + dayOfYear(gm - 1)

        var days = 365L * gy + (gy / 33) * 8 + (gy % 33 + 3) / 4 + gd2 - 386211

        jy += (days / 1461).toInt()
        days %= 1461

        var iy2 = ((days - 1) / 365).toInt()
        if (iy2 == 4) iy2 = 3
        days -= 365L * iy2

        jy += iy2

        val jm = if (days < 186) (1 + days / 31) else (7 + (days - 186) / 30)
        val jd = if (days < 186) (1 + days % 31) else (1 + (days - 186) % 30)

        return Jalali(jy, jm.toInt(), jd.toInt())
    }

    // Returns a Calendar shifted to the first day of the Jalali month that contains `base` + offsetMonths.
    fun jalaliMonthRange(base: Calendar, offsetMonths: Int): Pair<Long, Long> {
        val j = fromGregorian(base)
        // Approximate: build from Jalali y/m, convert back via a lookup.
        var (y, m) = j.year to j.month
        var off = offsetMonths
        while (off > 0) { m++; if (m > 12) { m = 1; y++ }; off-- }
        while (off < 0) { m--; if (m < 1) { m = 12; y-- }; off++ }

        val startCal = toGregorian(y, m, 1)
        val endCal = (Calendar.getInstance().apply {
            time = startCal.time
            add(Calendar.MONTH, 1)
        })
        return startCal.timeInMillis to (endCal.timeInMillis - 1)
    }

    fun monthLabel(base: Calendar, offsetMonths: Int): String {
        val j = fromGregorian(base)
        var (y, m) = j.year to j.month
        var off = offsetMonths
        while (off > 0) { m++; if (m > 12) { m = 1; y++ }; off-- }
        while (off < 0) { m--; if (m < 1) { m = 12; y-- }; off++ }
        return "${persianMonths[m - 1]} $y"
    }

    // e.g. "۱۲ تیر ۱۴۰۳"
    fun formatDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val j = fromGregorian(cal)
        return "${j.day} ${persianMonths[j.month - 1]} ${j.year}"
    }

    // e.g. "۱۲ تیر ۱۴۰۳، ۱۴:۳۰"
    fun formatDateTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val j = fromGregorian(cal)
        val hh = cal.get(Calendar.HOUR_OF_DAY)
        val mm = cal.get(Calendar.MINUTE)
        return "${j.day} ${persianMonths[j.month - 1]} ${j.year}، ${"%02d:%02d".format(hh, mm)}"
    }

    private fun dayOfYear(month: Int): Int {
        // cumulative days before given (1-based) month in a non-leap year
        val cum = intArrayOf(0, 31, 62, 93, 124, 155, 186, 216, 246, 276, 306, 336)
        return if (month == 0) 0 else cum[month - 1]
    }

    private fun isLeap(jy: Int): Boolean {
        return (jy % 33 == 1 || jy % 33 == 5 || jy % 33 == 9 || jy % 33 == 13 ||
                jy % 33 == 17 || jy % 33 == 22 || jy % 33 == 26 || jy % 33 == 30)
    }

    // Convert Jalali (y, m, d) to a Gregorian Calendar using the standard algorithm.
    private fun toGregorian(jy: Int, jm: Int, jd: Int): Calendar {
        var gy = if (jy > 979) 1600 else 621
        var jump = 0
        var theJy = jy - (if (jy > 979) 979 else 0)

        while (theJy - jump >= 33) { gy += 33; jump += 33 }
        gy += (theJy - jump) / 4 - (theJy - jump) * 3
        theJy -= (theJy - jump) / 33 * 33

        val days = (if (theJy % 33 == 4 || theJy % 33 == 1) 1 else 0)
        gy += 2820 - days

        var gd = jd + (if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186)
        var gm = if (gd > 365) { gd -= 365; 13 } else 0

        while (gd > (if (gm == 0) 31 else if (gm <= 6) 31 else 30)) {
            gd -= if (gm == 0) 31 else if (gm <= 6) 31 else 30
            gm++
        }

        val cal = Calendar.getInstance(Locale("fa", "IR"))
        cal.set(gy, gm - 1, gd, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }
}
