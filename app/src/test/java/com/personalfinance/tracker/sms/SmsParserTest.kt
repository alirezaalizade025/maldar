package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun prefixMinusLine_isParsedAsExpense() {
        val message = """
            10.12770592.1
            -6,363,000
            05/05_00:28
            مانده: 374,258,482
        """.trimIndent()

        val result = SmsParser.parse(message)

        assertEquals(636_300.0, result.amount!!, 0.001)
        assertEquals(TxType.EXPENSE, result.type)
        assertEquals(37_425_848.2, result.balanceAfter!!, 0.001)
        assertTrue(SmsParser.looksLikeTransaction(message))
    }

    @Test
    fun suffixPlusLine_isParsedAsIncome() {
        val message = """
            640370016589325001
            44,000,000+
            1405/5/1-19:01
            مانده:46,338,724
        """.trimIndent()

        val result = SmsParser.parse(message)

        assertEquals(4_400_000.0, result.amount!!, 0.001)
        assertEquals(TxType.INCOME, result.type)
        assertEquals(4_633_872.4, result.balanceAfter!!, 0.001)
        assertTrue(SmsParser.looksLikeTransaction(message))
    }

    @Test
    fun dateTimeMinus_isNotParsedAsSignedAmount() {
        val message = """
            640370016589325001
            1405/5/1-19:01
            مانده:46,338,724
        """.trimIndent()

        val result = SmsParser.parse(message)

        assertNull(result.amount)
        assertNull(result.type)
        assertFalse(SmsParser.looksLikeTransaction(message))
    }

    @Test
    fun signedBalanceOnSeparateLine_isNotParsedAsTransaction() {
        val message = """
            مانده:
            +374,258,482
        """.trimIndent()

        val result = SmsParser.parse(message)

        assertNull(result.amount)
        assertNull(result.type)
        assertEquals(37_425_848.2, result.balanceAfter!!, 0.001)
        assertFalse(SmsParser.looksLikeTransaction(message))
    }
}
