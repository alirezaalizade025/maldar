package com.personalfinance.tracker.util

import com.personalfinance.tracker.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataExportTest {
    @Test
    fun csv_roundTrip_preservesMultilineFieldsAndLoanDetails() {
        val tx = TransactionEntity(
            id = 10,
            amount = 1_234_567.0,
            type = TxType.EXPENSE,
            category = "خانه, زندگی",
            note = "خط اول\nخط دوم",
            dateMillis = 12345,
            bankAccountId = 3,
            rawSms = "برداشت، آزمایشی\nمانده",
            balanceAfter = 9_876_543.0,
            loanId = 7
        )
        val account = BankAccountEntity(3, "بانک", "کارت", "4321", 9_876_543.0)
        val loan = LoanEntity(
            id = 7,
            name = "وام",
            principal = 12_000_000.0,
            remainingAmount = 8_000_000.0,
            dueDateMillis = 555,
            payDayOfMonth = 6,
            installment = 1_000_000.0,
            totalMonths = 12,
            bankAccountId = 3,
            notes = "توضیح\nوام"
        )
        val stock = StockAssetEntity(
            instrumentCode = "2400322364771558",
            symbol = "شستا",
            name = "سرمایه گذاری تامین اجتماعی",
            quantity = 12_345.0,
            buyPriceToman = 210.5,
            lastPriceToman = 229.3,
            lastPriceUpdatedAt = 999L
        )

        val csv = DataExport.toCsv(
            listOf(tx),
            listOf(account),
            listOf(loan),
            emptyList(),
            stockAssets = listOf(stock)
        )
        val restored = DataExport.fromCsv(csv)

        assertEquals(tx, restored.transactions.single())
        assertEquals(account, restored.accounts.single())
        assertEquals(loan, restored.loans.single())
        assertEquals(stock, restored.stockAssets.single())
    }

    @Test
    fun csv_import_repairsLegacyUngroupedThousandsColumns() {
        val csv = """
            --- Transactions ---
            id,type,amount,category,note,dateMillis,bankAccountId,loanId,source,balanceAfter
            1,EXPENSE,1,234,567.00,خرید,یادداشت,1000,2,,MANUAL,9,876,543.00
        """.trimIndent()

        val restored = DataExport.fromCsv(csv).transactions.single()
        assertEquals(1_234_567.0, restored.amount, 0.0)
        assertEquals(9_876_543.0, restored.balanceAfter ?: 0.0, 0.0)
        assertTrue(restored.rawSms == null)
    }

    @Test
    fun json_roundTrip_preservesStockAssetDetails() {
        val stock = StockAssetEntity(
            instrumentCode = "2400322364771558",
            symbol = "شستا",
            name = "سرمایه گذاری تامین اجتماعی",
            quantity = 1000.0,
            buyPriceToman = 200.0,
            lastPriceToman = 229.3,
            lastPriceUpdatedAt = 123456L
        )

        val json = DataExport.toJson(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            stockAssets = listOf(stock)
        )
        val restored = DataExport.fromJson(json)

        assertEquals(stock, restored.stockAssets.single())
    }
}
