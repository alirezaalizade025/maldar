package com.personalfinance.tracker.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Decimal arithmetic in Toman; existing database/backup values retain their precision. */
object DecimalMoney {
    // shortcut: retain Double storage for backup compatibility; migrate units when the currency precision contract changes.
    private fun decimal(value: Double): BigDecimal {
        require(value.isFinite()) { "Invalid monetary amount." }
        return BigDecimal.valueOf(value)
    }

    private fun finite(value: BigDecimal): Double = value.toDouble().also {
        require(it.isFinite()) { "Monetary amount is too large." }
    }

    fun add(a: Double, b: Double): Double = finite(decimal(a) + decimal(b))
    fun subtract(a: Double, b: Double): Double = finite(decimal(a) - decimal(b))
    fun multiply(a: Double, b: Double): Double = finite(decimal(a) * decimal(b))
    fun average(total: Double, count: Int): Double = finite(decimal(total).divide(BigDecimal(count), 2, RoundingMode.HALF_UP))
    fun <T> sum(values: Iterable<T>, amount: (T) -> Double): Double =
        finite(values.fold(BigDecimal.ZERO) { total, row -> total + decimal(amount(row)) })
}
