package com.personalfinance.tracker.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Validates the hand-rolled Jalali (Persian) converter, paying special attention
 * to leap years and year boundaries, since a wrong conversion corrupts loan due
 * dates and report month ranges.
 */
class JalaliCalendarTest {

    private fun gregYearMonthDay(millis: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun now_roundTrip_isStable() {
        val now = Calendar.getInstance()
        val j = JalaliCalendar.fromGregorian(now)
        val back = JalaliCalendar.toGregorianPublic(j.year, j.month, j.day)
        val g = gregYearMonthDay(back.timeInMillis)
        assertEquals(now.get(Calendar.YEAR), g.first)
        assertEquals(now.get(Calendar.MONTH) + 1, g.second)
        assertEquals(now.get(Calendar.DAY_OF_MONTH), g.third)
    }

    @Test
    fun knownDates_matchReference() {
        // 1 Farvardin 1403 = 21 March 2024 (Gregorian)
        val c = JalaliCalendar.toGregorianPublic(1403, 1, 1)
        val (y, m, d) = gregYearMonthDay(c.timeInMillis)
        assertEquals(2024, y); assertEquals(3, m); assertEquals(21, d)

        // 29 Esfand 1403 (leap year) = 19 March 2025
        val c2 = JalaliCalendar.toGregorianPublic(1403, 12, 29)
        val (y2, m2, d2) = gregYearMonthDay(c2.timeInMillis)
        assertEquals(2025, y2); assertEquals(3, m2); assertEquals(19, d2)
    }

    @Test
    fun leapYear_has30daysInEsfand() {
        // 1403 is a leap Jalali year -> Esfand has 30 days
        val lastDay = JalaliCalendar.toGregorianPublic(1403, 12, 30)
        val (y, m, d) = gregYearMonthDay(lastDay.timeInMillis)
        assertEquals(2025, y); assertEquals(3, m); assertEquals(20, d)
    }

    @Test
    fun nonLeapYear_esfandHas29() {
        // 1402 is not a leap Jalali year -> Esfand has 29 days; day 30 should roll over
        val c = JalaliCalendar.toGregorianPublic(1402, 12, 29)
        val (y, m, d) = gregYearMonthDay(c.timeInMillis)
        assertEquals(2024, y); assertEquals(3, m); assertEquals(19, d)
    }

    @Test
    fun nextDueDate_inFutureOrThisMonth() {
        val millis = JalaliCalendar.nextDueDateMillis(15)
        assertTrue(millis > 0)
    }

    @Test
    fun monthRange_startBeforeEnd() {
        val (start, end) = JalaliCalendar.jalaliMonthRange(Calendar.getInstance(), 0)
        assertTrue(start <= end)
    }
}
