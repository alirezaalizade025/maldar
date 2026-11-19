package com.personalfinance.tracker.data

import com.personalfinance.tracker.util.JalaliCalendar
import java.util.Calendar

/** A full-month estimate; spending is counted only once, from expense transactions. */
data class CategoryBudget(val category: String, val estimated: Double, val spent: Double) {
    val remaining: Double get() = DecimalMoney.subtract(estimated, spent)
    val usedPercent: Double? get() = if (estimated > 0.0) spent / estimated * 100.0 else null
}

data class MonthlyBudgetSummary(
    val startMillis: Long,
    val endMillis: Long,
    val daysInMonth: Int,
    val categories: List<CategoryBudget>,
    val asOfMillis: Long
) {
    val estimated: Double get() = DecimalMoney.sum(categories) { it.estimated }
    val spent: Double get() = DecimalMoney.sum(categories) { it.spent }
    val remaining: Double get() = DecimalMoney.subtract(estimated, spent)
    val usedPercent: Double? get() = if (estimated > 0.0) spent / estimated * 100.0 else null
}

fun calculateMonthlyBudget(
    transactions: List<TransactionEntity>,
    categoryNames: List<String>,
    now: Calendar = Calendar.getInstance(),
    monthOffset: Int = 0
): MonthlyBudgetSummary {
    val (start, end) = JalaliCalendar.jalaliMonthRange(now, monthOffset)
    val history = (1..3).map { JalaliCalendar.jalaliMonthRange(now, monthOffset - it) }
    val historicalTotals = mutableMapOf<String, Double>()
    val spending = mutableMapOf<String, Double>()
    for (tx in reportingTransactions(transactions, now.timeInMillis)) {
        if (tx.type != TxType.EXPENSE || !tx.amount.isFinite() || tx.amount <= 0.0 || tx.dateMillis > now.timeInMillis) continue
        when {
            tx.dateMillis in start..end -> spending[tx.category] = DecimalMoney.add(spending[tx.category] ?: 0.0, tx.amount)
            history.any { (a, b) -> tx.dateMillis in a..b } ->
                historicalTotals[tx.category] = DecimalMoney.add(historicalTotals[tx.category] ?: 0.0, tx.amount)
        }
    }
    val categories = (categoryNames + historicalTotals.keys + spending.keys).distinct().map { name ->
        // Always divide by three: zero-spending months are part of the agreed estimate.
        CategoryBudget(name, DecimalMoney.average(historicalTotals[name] ?: 0.0, 3), spending[name] ?: 0.0)
    }.sortedWith(compareByDescending<CategoryBudget> { it.estimated }.thenBy { it.category })
    val lastDay = Calendar.getInstance(now.timeZone).apply { timeInMillis = end }
    return MonthlyBudgetSummary(start, end, JalaliCalendar.fromGregorian(lastDay).day, categories, now.timeInMillis)
}
