package com.personalfinance.tracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TsetmcStockServiceTest {

    @Test
    fun search_parsesOrdinarySharesAndFiltersDerivatives() {
        val json = """
            {
              "instrumentSearch": [
                {
                  "insCode": "2400322364771558",
                  "lVal30": "سرمايه گذاري تامين اجتماعي",
                  "lVal18AFC": "شستا",
                  "flowTitle": "بازار بورس"
                },
                {
                  "insCode": "53649026997509688",
                  "lVal30": "آتي شستا-1405/06/11",
                  "lVal18AFC": "جستا0506",
                  "flowTitle": "بازار مشتقه"
                }
              ]
            }
        """.trimIndent()

        val result = TsetmcStockService.parseSearch(json, "شستا")

        assertEquals(1, result.size)
        assertEquals("2400322364771558", result.single().instrumentCode)
        assertEquals("شستا", result.single().symbol)
        assertEquals("سرمايه گذاري تامين اجتماعي", result.single().name)
    }

    @Test
    fun quote_prefersLastTradeAndConvertsRialToToman() {
        val json = """
            {
              "closingPriceInfo": {
                "priceYesterday": 2361.00,
                "pClosing": 2291.00,
                "pDrCotVal": 2293.00
              }
            }
        """.trimIndent()

        val quote = TsetmcStockService.parseQuote(json, "2400322364771558", 123L)

        assertEquals(229.3, quote.lastPriceToman, 0.0)
        assertEquals(229.1, quote.closingPriceToman ?: 0.0, 0.0)
        assertEquals(123L, quote.fetchedAtMillis)
    }

    @Test
    fun quote_fallsBackToClosingPrice() {
        val json = """{"closingPriceInfo":{"pClosing":15000,"pDrCotVal":0}}"""

        val quote = TsetmcStockService.parseQuote(json, "1")

        assertEquals(1500.0, quote.lastPriceToman, 0.0)
        assertTrue(quote.lastPriceToman > 0.0)
    }
}
