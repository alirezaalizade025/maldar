package com.personalfinance.tracker.data

import org.junit.Assert.*
import org.junit.Test

class LoanPaymentMatchingTest {
    @Test fun partialFinalInstallment_stillShowsUnpaidPart() {
        val loan = LoanEntity(id = 1, name = "loan", principal = 100.0, remainingAmount = 10.0,
            dueDateMillis = 0, installment = 30.0)
        val payment = TransactionEntity(amount = 20.0, type = TxType.EXPENSE, category = "وام",
            dateMillis = System.currentTimeMillis(), loanId = 1)
        assertEquals(10.0, loanRemainingThisMonth(loan, listOf(loan), listOf(payment)), 0.0)
    }
    @Test fun explicitlyLinkedIncome_isNotARepayment() {
        val loan = LoanEntity(id = 1, name = "loan", principal = 100.0, remainingAmount = 100.0, dueDateMillis = 0)
        val tx = TransactionEntity(amount = 10.0, type = TxType.INCOME, category = "وام", dateMillis = 0, loanId = 1)
        assertFalse(tx.matchesLoanPayment(loan, listOf(loan)))
    }
}
