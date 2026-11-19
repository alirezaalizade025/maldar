package com.personalfinance.tracker.data

import org.junit.Assert.*
import org.junit.Test

class TransactionReportTest {
    @Test fun reportUsesExplicitCutoffForFutureRepayments() {
        val parent = TransactionEntity(id = 1, amount = 100.0, type = TxType.EXPENSE,
            category = "food", dateMillis = 100, bankAccountId = 1)
        val repayment = parent.copy(id = 2, amount = 20.0, type = TxType.INCOME,
            parentTransactionId = 1, dateMillis = 200, bankAccountId = 2)
        val rows = listOf(parent, repayment)
        assertEquals(100.0, transactionReport(rows, 0, 300, accountId = 1, asOfMillis = 150).expense, 0.0)
        val afterPayment = transactionReport(rows, 0, 300, accountId = 1, asOfMillis = 250)
        assertEquals(80.0, afterPayment.expense, 0.0)
        assertEquals(80.0, afterPayment.categories.single().total, 0.0)
        assertEquals(0.0, transactionReport(rows, 0, 300, accountId = 2, asOfMillis = 250).income, 0.0)
    }

    @Test fun totalsUseDecimalArithmeticAndCountTransfersOnce() {
        val template = TransactionEntity(amount = 0.1, type = TxType.EXPENSE, category = "food", dateMillis = 100)
        val rows = listOf(template, template.copy(amount = 0.2),
            template.copy(amount = 1.0, type = TxType.CARD_TO_CARD, bankAccountId = 1, toAccountId = 2))
        val report = transactionReport(rows, 0, 200, asOfMillis = 200)
        assertEquals(0.3, report.expense, 0.0)
        assertEquals(0.3, report.categories.single().total, 0.0)
        assertEquals(1.0, report.transfers, 0.0)
    }
}
