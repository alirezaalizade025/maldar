package com.personalfinance.tracker.data

import com.personalfinance.tracker.util.JalaliCalendar
import kotlin.math.abs

/** Shared recognition for explicit and legacy loan-payment transactions. */
fun TransactionEntity.matchesLoanPayment(loan: LoanEntity, loans: List<LoanEntity>): Boolean {
    if (loanId != null) return loanId == loan.id
    if (type != TxType.EXPENSE || category.trim() != "وام") return false
    if (note.contains(loan.name, ignoreCase = true)) return true

    val due = if (loan.installment > 0.0) loan.installment else loan.remainingAmount
    if (abs(amount - due) >= 0.01) return false
    return loans.count {
        val otherDue = if (it.installment > 0.0) it.installment else it.remainingAmount
        abs(amount - otherDue) < 0.01
    } == 1
}

/** Monthly installment due this period, capped at the remaining balance. */
fun loanMonthlyDue(loan: LoanEntity): Double =
    (if (loan.installment > 0.0) loan.installment else loan.remainingAmount).coerceAtMost(loan.remainingAmount)

/** Sum of payments matched to [loan] within the current (Jalali) month. */
fun loanPaidThisMonth(loan: LoanEntity, loans: List<LoanEntity>, transactions: List<TransactionEntity>): Double =
    transactions.filter { it.matchesLoanPayment(loan, loans) && JalaliCalendar.isInJalaliMonth(it.dateMillis) }
        .sumOf { it.amount }

/** Remaining installment still owed this month after payments (never negative). */
fun loanRemainingThisMonth(loan: LoanEntity, loans: List<LoanEntity>, transactions: List<TransactionEntity>): Double =
    (loanMonthlyDue(loan) - loanPaidThisMonth(loan, loans, transactions)).coerceAtLeast(0.0)
