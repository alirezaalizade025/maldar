package com.personalfinance.tracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MetalPriceServiceTest {
    @Test
    fun parsesTgjuCurrentPriceFromHtml() {
        val html = """
            <span class="price" data-col="info.last_trade.PDrCotVal">183,413,000</span>
        """.trimIndent()
        assertEquals(183_413_000.0, MetalPriceService.currentPrice(html), 0.0)
    }
}
