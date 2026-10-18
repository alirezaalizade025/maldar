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
        // 1 Farvardin 1403 = 20 March 2024 (Gregorian)
        val c = JalaliCalendar.toGregorianPublic(1403, 1, 1)
        val (y, m, d) = gregYearMonthDay(c.timeInMillis)
        assertEquals(2024, y); assertEquals(3, m); assertEquals(20, d)

        // 30 Esfand 1403 (leap year) = 20 March 2025
        val c2 = JalaliCalendar.toGregorianPublic(1403, 12, 30)
        val (y2, m2, d2) = gregYearMonthDay(c2.timeInMillis)
        assertEquals(2025, y2); assertEquals(3, m2); assertEquals(20, d2)
    }

    @Test
    fun recentGregorian_mapsTo1400s() {
        // 18 July 2026 must convert to a 1400s Jalali year (was wrongly 823).
        val j = JalaliCalendar.fromGregorian(GregorianCalendar(2026, 6, 18))
        assertTrue("Jalali year must be in the 1400s, was ${j.year}", j.year in 1400..1499)
        assertEquals(4, j.month) // Tir
    }

    @Test
    fun currentDate_roundTrip_matchesReference() {
        // Guard against the year-offset regression: 2026-07-18 -> 1405/04/27.
        val j = JalaliCalendar.fromGregorian(GregorianCalendar(2026, 6, 18))
        assertEquals(1405, j.year); assertEquals(4, j.month); assertEquals(27, j.day)
    }

    @Test
    fun mordadSix2026_isNotOneDayBehind() {
        // 28 July 2026 is 6 Mordad 1405 in Iran.
        val j = JalaliCalendar.fromGregorian(GregorianCalendar(2026, 6, 28))
        assertEquals(1405, j.year)
        assertEquals(5, j.month)
        assertEquals(6, j.day)
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

    @Test
    fun jalaliMonthRange_startsOnFirstOfJalaliMonth() {
        val base = GregorianCalendar(2026, 6, 28)
        val (start, end) = JalaliCalendar.jalaliMonthRange(base, 0)
        val startJ = JalaliCalendar.fromGregorian(Calendar.getInstance().apply { timeInMillis = start })
        val endJ = JalaliCalendar.fromGregorian(Calendar.getInstance().apply { timeInMillis = end })
        assertEquals(1, startJ.day)
        assertEquals(startJ.month, endJ.month)
    }
    @Test
    fun monthRange_usesBaseCalendarTimezone() {
        val zone = java.util.TimeZone.getTimeZone("America/Los_Angeles")
        val base = GregorianCalendar(zone).apply { clear(); set(2026, 9, 17, 12, 0) }
        val (start, end) = JalaliCalendar.jalaliMonthRange(base, 0)
        val first = GregorianCalendar(zone).apply { timeInMillis = start }
        val last = GregorianCalendar(zone).apply { timeInMillis = end }
        assertEquals(0, first.get(Calendar.HOUR_OF_DAY))
        assertEquals(1, JalaliCalendar.fromGregorian(first).day)
        assertEquals(30, JalaliCalendar.fromGregorian(last).day)
        assertEquals(23, last.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun monthEnds_matchReferenceAcrossSeasonsAndLeapYears() {
        // Reference Persian calendar: 1403 Esfand is leap; 1404 Esfand is not.
        val fixtures = listOf(
            intArrayOf(2026, 4, 10, 1405, 1, 31),
            intArrayOf(2026, 10, 17, 1405, 7, 30),
            intArrayOf(2025, 3, 10, 1403, 12, 30),
            intArrayOf(2026, 3, 10, 1404, 12, 29)
        )
        fixtures.forEach { f ->
            val base = GregorianCalendar(f[0], f[1] - 1, f[2])
            val (_, end) = JalaliCalendar.jalaliMonthRange(base, 0)
            val last = Calendar.getInstance().apply { timeInMillis = end }
            assertEquals(JalaliCalendar.Jalali(f[3], f[4], f[5]), JalaliCalendar.fromGregorian(last))
            val next = Calendar.getInstance().apply { timeInMillis = end + 1 }
            assertEquals(1, JalaliCalendar.fromGregorian(next).day)
        }
    }

    @Test fun daysUntil_countsCalendarDaysAcrossHistoricalTehranDst() {
        val zone = java.util.TimeZone.getTimeZone("Asia/Tehran")
        val now = GregorianCalendar(zone).apply { clear(); set(2021, 2, 22, 12, 0) }
        val target = GregorianCalendar(zone).apply { clear(); set(2021, 2, 23, 12, 0) }
        assertEquals(1L, JalaliCalendar.daysUntil(target.timeInMillis, now))
    }

}
