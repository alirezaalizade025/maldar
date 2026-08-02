package com.personalfinance.tracker.data

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
