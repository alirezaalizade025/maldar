package com.personalfinance.tracker.data

import com.personalfinance.tracker.util.Digits
import java.util.Locale
import java.math.BigDecimal

/** Reporting copies only; persisted amounts always describe the actual bank movement. */
fun reportingTransactions(transactions: List<TransactionEntity>, asOfMillis: Long = System.currentTimeMillis()): List<TransactionEntity> {
    val current = transactions.filter { it.dateMillis <= asOfMillis }
    val repayments = current.filter { it.parentTransactionId != null && it.type == TxType.INCOME }
        .groupBy { it.parentTransactionId }.mapValues { (_, rows) -> repaymentTotal(rows) }
    return current.filter { it.parentTransactionId == null }.map { tx ->
        if (tx.type == TxType.EXPENSE && tx.amount.isFinite()) tx.copy(amount =
            (tx.amount.toBigDecimal() - (repayments[tx.id] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO).toDouble()) else tx
    }
}

private fun repaymentTotal(rows: List<TransactionEntity>): BigDecimal =
    rows.fold(BigDecimal.ZERO) { total, tx -> total + tx.amount.toBigDecimal() }

internal fun shareRemainingAmount(parent: TransactionEntity, transactions: List<TransactionEntity>): Double =
    (parent.amount.toBigDecimal() - repaymentTotal(transactions.filter { it.parentTransactionId == parent.id })).toDouble()

internal fun validateShareLinks(transactions: List<TransactionEntity>) {
    val byId = transactions.associateBy { it.id }
    val children = transactions.filter { it.parentTransactionId != null }
    children.forEach { child ->
        val parent = requireNotNull(byId[child.parentTransactionId]) { "هزینهٔ اصلی پیدا نشد." }
        require(child.id != parent.id && child.type == TxType.INCOME && child.loanId == null &&
            parent.type == TxType.EXPENSE && parent.loanId == null && parent.parentTransactionId == null) {
            "فقط واریز را می‌توان به هزینهٔ اصلی بدون قسط متصل کرد."
        }
    }
    children.groupBy { it.parentTransactionId }.forEach { (id, rows) ->
        require(repaymentTotal(rows) <= byId.getValue(id!!).amount.toBigDecimal()) { "جمع بازپرداخت‌ها از مبلغ اصلی بیشتر است." }
    }
}

data class TransactionFilter(
    val query: String = "", val accountId: Long? = null, val category: String? = null,
    val type: TxType? = null, val fromMillis: Long? = null, val toMillis: Long? = null,
    val minAmount: Double? = null, val maxAmount: Double? = null
)

private fun normalizedSearch(value: String): String = Digits.toEnglish(value)
    .lowercase(Locale.ROOT).replace('ي', 'ی').replace('ك', 'ک')
    .replace("٬", "").replace(",", "").replace('\u200c', ' ')

fun searchTransactions(transactions: List<TransactionEntity>, accounts: List<BankAccountEntity>, filter: TransactionFilter): List<TransactionEntity> {
    val accountText = accounts.associate { it.id to "${it.accountLabel} ${it.bankName} ${it.accountLast4}" }
    val terms = normalizedSearch(filter.query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    return transactions.filter { tx ->
        (filter.accountId == null || tx.bankAccountId == filter.accountId || tx.toAccountId == filter.accountId) &&
            (filter.category == null || tx.category == filter.category) && (filter.type == null || tx.type == filter.type) &&
            (filter.fromMillis == null || tx.dateMillis >= filter.fromMillis) &&
            (filter.toMillis == null || tx.dateMillis <= filter.toMillis) &&
            (filter.minAmount == null || tx.amount >= filter.minAmount) &&
            (filter.maxAmount == null || tx.amount <= filter.maxAmount) &&
            (terms.isEmpty() || normalizedSearch("${tx.category} ${tx.note} ${tx.rawSms.orEmpty()} ${tx.amount} ${accountText[tx.bankAccountId].orEmpty()} ${accountText[tx.toAccountId].orEmpty()}").let { text -> terms.all { it in text } })
    }.sortedByDescending { it.dateMillis }
}

internal val TransactionEntity.bankMovementMillis: Long
    get() = if (source == TxSource.SMS) smsTimestampMillis ?: dateMillis else dateMillis

fun accountMovement(tx: TransactionEntity, accountId: Long): Double =
    (if (tx.bankAccountId == accountId) if (tx.type == TxType.INCOME) tx.amount else -tx.amount else 0.0) +
        (if (tx.type == TxType.CARD_TO_CARD && tx.toAccountId == accountId) tx.amount else 0.0)

data class AccountBalanceCheck(val movementsAfter: Double, val expectedBalance: Double, val difference: Double)

fun checkAccountBalance(account: BankAccountEntity, bankBalance: Double, snapshotMillis: Long,
    transactions: List<TransactionEntity>, nowMillis: Long = System.currentTimeMillis()): AccountBalanceCheck {
    require(bankBalance.isFinite())
    val movements = transactions.filter {
        val time = it.bankMovementMillis
        time > snapshotMillis && time <= nowMillis
    }
        .sumOf { accountMovement(it, account.id) }
    val expected = bankBalance + movements
    return AccountBalanceCheck(movements, expected, account.balance - expected)
}
