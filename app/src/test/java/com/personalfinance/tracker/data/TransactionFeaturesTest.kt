package com.personalfinance.tracker.data

import org.junit.Assert.*
import org.junit.Test

class TransactionFeaturesTest {
    private val main = TransactionEntity(id = 1, amount = 1000.0, type = TxType.EXPENSE,
        category = "خرید", note = "غذاي مشترک", dateMillis = 100, bankAccountId = 1)
    private fun repayment(id: Long, amount: Double) = main.copy(id = id, amount = amount,
        type = TxType.INCOME, parentTransactionId = 1, dateMillis = 200)

    @Test fun shareUsesNetExpenseAndDoesNotCountRepaymentsAsIncome() {
        val raw = listOf(main, repayment(2, 200.0), repayment(3, 300.0))
        val reports = reportingTransactions(raw, 1000)
        assertEquals(500.0, reports.single().amount, 0.0)
        assertEquals(1000.0, raw.first().amount, 0.0)
        assertEquals(reports, reportingTransactions(reports, 1000))
    }
    @Test fun futureRepaymentDoesNotReduceCurrentExpense() {
        assertEquals(1000.0, reportingTransactions(listOf(main, repayment(2, 200.0)), 150).single().amount, 0.0)
    }
    @Test fun searchNormalizesPersianTextAndDigitsAndMatchesDestination() {
        val transfer = main.copy(id = 4, type = TxType.CARD_TO_CARD, toAccountId = 2)
        assertEquals(listOf(main), searchTransactions(listOf(main), emptyList(), TransactionFilter(query = "غذای ۱٬۰۰۰")))
        assertEquals(listOf(transfer), searchTransactions(listOf(main, transfer), emptyList(), TransactionFilter(accountId = 2)))
        assertTrue(searchTransactions(listOf(main), emptyList(), TransactionFilter(minAmount = 1001.0)).isEmpty())
        assertTrue(searchTransactions(listOf(main), emptyList(), TransactionFilter(fromMillis = 101)).isEmpty())
        assertTrue(searchTransactions(listOf(main), emptyList(), TransactionFilter(type = TxType.INCOME)).isEmpty())
    }
    @Test fun bankCheckUsesOriginalSmsTimeAfterEditingDisplayDate() {
        val sms = main.copy(source = TxSource.SMS, smsTimestampMillis = 100, dateMillis = 300, amount = 20.0)
        val later = main.copy(id = 2, dateMillis = 200, amount = 10.0)
        val account = BankAccountEntity(id = 1, bankName = "", accountLabel = "", accountLast4 = "", balance = 70.0)
        assertEquals(0.0, checkAccountBalance(account, 80.0, 100, listOf(sms, later), 1000).difference, 0.0)
    }

    @Test fun bankCheckReplaysOnlyMovementsAfterSnapshotAndBothTransferSides() {
        val txs = listOf(main, repayment(2, 200.0), main.copy(id = 3, amount = 50.0,
            type = TxType.CARD_TO_CARD, bankAccountId = 2, toAccountId = 1, dateMillis = 300))
        val account = BankAccountEntity(id = 1, bankName = "", accountLabel = "", accountLast4 = "", balance = 1150.0)
        val result = checkAccountBalance(account, 900.0, 100, txs, 1000)
        assertEquals(250.0, result.movementsAfter, 0.0)
        assertEquals(1150.0, result.expectedBalance, 0.0)
        assertEquals(0.0, result.difference, 0.0)
        assertEquals(-100.0, checkAccountBalance(account, 1250.0, 1000, txs, 1000).difference, 0.0)
    }
}
