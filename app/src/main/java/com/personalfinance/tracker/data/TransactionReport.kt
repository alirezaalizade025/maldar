package com.personalfinance.tracker.data

data class TransactionReport(
    val income: Double,
    val expense: Double,
    val transfers: Double,
    val categories: List<CategoryTotal>
)

fun transactionReport(
    transactions: List<TransactionEntity>, start: Long, end: Long,
    accountId: Long? = null, asOfMillis: Long = System.currentTimeMillis()
): TransactionReport {
    val rows = reportingTransactions(transactions, asOfMillis).filter {
        it.dateMillis in start..end && (accountId == null || it.bankAccountId == accountId || it.toAccountId == accountId)
    }
    fun total(type: TxType) = DecimalMoney.sum(rows.filter { it.type == type }) { it.amount }
    val categories = rows.filter { it.type == TxType.EXPENSE }.groupBy { it.category }
        .map { (name, entries) -> CategoryTotal(name, DecimalMoney.sum(entries) { it.amount }) }
        .sortedByDescending { it.total }
    return TransactionReport(total(TxType.INCOME), total(TxType.EXPENSE), total(TxType.CARD_TO_CARD), categories)
}
