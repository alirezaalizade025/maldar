package com.personalfinance.tracker.data

import com.personalfinance.tracker.util.JalaliCalendar
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class MonthlyBudgetTest {
    private val zone = TimeZone.getTimeZone("Asia/Tehran")
    private fun date(year: Int, month: Int, day: Int) = GregorianCalendar(zone).apply {
        clear(); set(year, month - 1, day, 12, 0)
    }
    private val now get() = date(2026, 10, 17) // 25 Mehr 1405, a 30-day month.
    private fun expense(month: Int, amount: Double, category: String = "food", day: Int = 1) =
        TransactionEntity(amount = amount, type = TxType.EXPENSE, category = category,
            dateMillis = date(2026, month, day).timeInMillis)

    @Test fun shareUsesNetExpenseInParentMonthIncludingCrossMonthRepayment() {
        val parent = expense(9, 900.0).copy(id = 1)
        val repayment = expense(10, 300.0).copy(id = 2, type = TxType.INCOME, parentTransactionId = 1)
        val current = expense(10, 200.0).copy(id = 3)
        val result = calculateMonthlyBudget(listOf(parent, repayment, current), emptyList(), now)
        assertEquals(200.0, result.estimated, 0.0)
        assertEquals(200.0, result.spent, 0.0)
    }

    @Test fun averagesThreeCompletedJalaliMonths_andTracksCurrentUsage() {
        val result = calculateMonthlyBudget(listOf(expense(7, 600.0), expense(8, 900.0),
            expense(9, 1200.0), expense(10, 360.0)), listOf("food"), now)
        val food = result.categories.single()
        assertEquals(900.0, food.estimated, 0.0)
        assertEquals(360.0, food.spent, 0.0)
        assertEquals(40.0, food.usedPercent!!, 0.0)
        assertEquals(540.0, food.remaining, 0.0)
        assertEquals(30, result.daysInMonth)
        assertEquals(JalaliCalendar.Jalali(1405, 7, 30), JalaliCalendar.fromGregorian(
            GregorianCalendar(zone).apply { timeInMillis = result.endMillis }))
    }
    @Test fun missingMonthsCountAsZero_andOverspendingIsVisible() {
        val food = calculateMonthlyBudget(listOf(expense(7, 600.0), expense(10, 300.0)),
            listOf("food"), now).categories.single()
        assertEquals(200.0, food.estimated, 0.0)
        assertEquals(150.0, food.usedPercent!!, 0.0)
        assertEquals(-100.0, food.remaining, 0.0)
    }
    @Test fun overallUsageUsesTotals_notAverageOfCategoryPercentages() {
        val txs = (7..9).flatMap { listOf(expense(it, 900.0), expense(it, 100.0, "travel")) } +
            listOf(expense(10, 450.0), expense(10, 80.0, "travel"))
        val result = calculateMonthlyBudget(txs, listOf("food", "travel"), now)
        assertEquals(1000.0, result.estimated, 0.0)
        assertEquals(530.0, result.spent, 0.0)
        assertEquals(53.0, result.usedPercent!!, 0.0)
        assertEquals(470.0, result.remaining, 0.0)
    }
    @Test fun noHistory_keepsSpendingVisibleWithoutInventingPercentage() {
        val result = calculateMonthlyBudget(listOf(expense(10, 60.0, "new")), listOf("unused"), now)
        assertEquals(0.0, result.estimated, 0.0)
        assertEquals(60.0, result.spent, 0.0)
        assertNull(result.usedPercent)
        assertNull(result.categories.single { it.category == "new" }.usedPercent)
        assertEquals(2, result.categories.size)
    }
    @Test fun excludesIncomeTransfersAndOlderMonths() {
        val e = expense(10, 500.0)
        val result = calculateMonthlyBudget(listOf(expense(6, 999.0), e.copy(type = TxType.INCOME),
            e.copy(type = TxType.CARD_TO_CARD), expense(7, 300.0), expense(10, 40.0)), emptyList(), now)
        assertEquals(100.0, result.estimated, 0.0)
        assertEquals(40.0, result.spent, 0.0)
    }
    @Test fun reportMonthOffsetUsesItsOwnPreviousThreeMonths() {
        val result = calculateMonthlyBudget(listOf(expense(6, 300.0), expense(7, 600.0),
            expense(8, 900.0), expense(9, 100.0), expense(10, 500.0)), emptyList(), now, -1)
        assertEquals(600.0, result.estimated, 0.0)
        assertEquals(100.0, result.spent, 0.0)
        assertEquals(31, result.daysInMonth)
    }
    @Test fun futureExpensesDoNotCountAsSpentYet() {
        val result = calculateMonthlyBudget(listOf(expense(7, 300.0), expense(10, 40.0),
            expense(10, 999.0, day = 18)), emptyList(), now)
        assertEquals(40.0, result.spent, 0.0)
    }
    @Test fun jalaliBoundaryIncludesMonthStartAndExcludesPreviousMillisecond() {
        // 1 Mehr 1405 = 23 September 2026 in Tehran.
        val start = GregorianCalendar(zone).apply { clear(); set(2026, 8, 23, 0, 0) }.timeInMillis
        val template = expense(10, 10.0)
        val result = calculateMonthlyBudget(listOf(template.copy(dateMillis = start),
            template.copy(dateMillis = start - 1, amount = 300.0)), emptyList(), now)
        assertEquals(start, result.startMillis)
        assertEquals(10.0, result.spent, 0.0)
        assertEquals(100.0, result.estimated, 0.0)
    }

}
