package com.personalfinance.tracker.data

import androidx.room.withTransaction

class FinanceRepository(private val db: AppDatabase) {

    // Bank accounts
    fun getBankAccounts() = db.bankAccountDao().getAll()
    suspend fun addBankAccount(account: BankAccountEntity) = db.bankAccountDao().insert(account)
    suspend fun updateBankAccount(account: BankAccountEntity) = db.bankAccountDao().update(account)
    suspend fun deleteBankAccount(account: BankAccountEntity) = db.bankAccountDao().delete(account)
    suspend fun getBankAccount(id: Long) = db.bankAccountDao().getById(id)

    fun getFinancialAssets() = db.financialAssetDao().getAll()
    suspend fun saveFinancialAsset(asset: FinancialAssetEntity) = db.financialAssetDao().upsert(asset)
    fun getStockAssets() = db.stockAssetDao().getAll()
    suspend fun saveStockAsset(asset: StockAssetEntity) = db.stockAssetDao().upsert(asset)
    suspend fun deleteStockAsset(asset: StockAssetEntity) = db.stockAssetDao().delete(asset)

    // SMS senders (dynamic list of numbers/ids to watch)
    fun getSmsSenders() = db.smsSenderDao().getAll()
    suspend fun getSmsSendersOnce() = db.smsSenderDao().getAllOnce()
    suspend fun addSmsSender(sender: SmsSenderEntity) = db.smsSenderDao().insert(sender)
    suspend fun deleteSmsSender(sender: SmsSenderEntity) = db.smsSenderDao().delete(sender)

    // Transactions (expenses + incomes)
    fun getTransactions() = db.transactionDao().getAll()
    fun getTransactionsBetween(start: Long, end: Long) = db.transactionDao().getBetween(start, end)

    suspend fun getTransactionsByAccount(accountId: Long): List<TransactionEntity> =
        db.transactionDao().getByAccount(accountId)

    // Inserts a transaction and, when it is linked to a bank account, records the
    // account's remaining balance right after it (balanceAfter) on the transaction
    // itself, and updates the account's current remained.
    // For CARD_TO_CARD transactions, the balance is not affected.
    // If balanceAfter is provided, it is used; otherwise it is calculated.
    suspend fun addTransaction(tx: TransactionEntity): Long = db.withTransaction {
        // CARD_TO_CARD moves money between two accounts: decrease the source and
        // increase the destination. Both balances are updated; balanceAfter is not
        // used because a card-to-card has no single "remaining" for one account.
        if (tx.type == TxType.CARD_TO_CARD) {
            tx.bankAccountId?.let { fromId ->
                db.bankAccountDao().getById(fromId)?.let { from ->
                    db.bankAccountDao().update(from.copy(balance = (from.balance - tx.amount).coerceAtLeast(0.0)))
                }
            }
            tx.toAccountId?.let { toId ->
                db.bankAccountDao().getById(toId)?.let { to ->
                    db.bankAccountDao().update(to.copy(balance = to.balance + tx.amount))
                }
            }
            return@withTransaction db.transactionDao().insert(tx)
        }

        val accountId = tx.bankAccountId
        if (accountId == null) {
            return@withTransaction db.transactionDao().insert(tx)
        }

        val account = requireNotNull(db.bankAccountDao().getById(accountId)) {
            "The selected bank account no longer exists."
        }
        val remained = tx.balanceAfter ?: run {
            val delta = if (tx.type == TxType.INCOME) tx.amount else -tx.amount
            (account.balance + delta).coerceAtLeast(0.0)
        }

        db.bankAccountDao().update(account.copy(balance = remained))
        db.transactionDao().insert(tx.copy(balanceAfter = remained))
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        db.transactionDao().delete(tx)
        // CARD_TO_CARD transactions affect two accounts; revert both sides.
        if (tx.type == TxType.CARD_TO_CARD) {
            tx.bankAccountId?.let { fromId ->
                db.bankAccountDao().getById(fromId)?.let { from ->
                    db.bankAccountDao().update(from.copy(balance = (from.balance + tx.amount).coerceAtLeast(0.0)))
                }
            }
            tx.toAccountId?.let { toId ->
                db.bankAccountDao().getById(toId)?.let { to ->
                    db.bankAccountDao().update(to.copy(balance = (to.balance - tx.amount).coerceAtLeast(0.0)))
                }
            }
            return
        }

        if (tx.bankAccountId != null) {
            val account = db.bankAccountDao().getById(tx.bankAccountId)
            if (account != null) {
                // Revert the effect of this transaction on the account's remained.
                val delta = if (tx.type == TxType.INCOME) -tx.amount else tx.amount
                db.bankAccountDao().update(account.copy(balance = (account.balance + delta).coerceAtLeast(0.0)))
            }
        }
    }

    suspend fun updateTransaction(tx: TransactionEntity) {
        // balanceAfter is a historical snapshot belonging only to this transaction.
        // Editing it must never rewrite the account's current live balance.
        db.transactionDao().update(tx)
    }

    suspend fun getLoanPayments(loanId: Long): List<TransactionEntity> =
        db.transactionDao().getPaymentsForLoan(loanId)

    suspend fun getLoanPaymentsBetween(start: Long, end: Long): List<TransactionEntity> =
        db.transactionDao().getPaymentsBetween(start, end)

    suspend fun totalByType(type: TxType, start: Long, end: Long): Double =
        db.transactionDao().sumByTypeBetween(type, start, end) ?: 0.0

    suspend fun netBetween(start: Long, end: Long): Double {
        val income = totalByType(TxType.INCOME, start, end)
        val expense = totalByType(TxType.EXPENSE, start, end)
        return income - expense
    }

    // Total remained = sum of each account's current stored balance. The stored
    // balance is kept in sync by transactions (add/delete) and by direct edits /
    // SMS refreshes, so it is the single source of truth for the main-page total.
    suspend fun totalAccountBalance(): Double {
        return db.bankAccountDao().getAllOnce().sumOf { it.balance }
    }

    suspend fun expenseByCategory(start: Long, end: Long): List<CategoryTotal> =
        db.transactionDao().expenseByCategoryBetween(start, end)

    // Pending SMS (awaiting user confirmation)
    fun getPendingSms() = db.pendingSmsDao().getPending()
    fun getReviewedSms() = db.pendingSmsDao().getReviewed()
    suspend fun addPendingSms(pending: PendingSmsEntity) = db.pendingSmsDao().insert(pending)
    suspend fun updatePendingSms(pending: PendingSmsEntity) = db.pendingSmsDao().update(pending)
    suspend fun deletePendingSms(pending: PendingSmsEntity) = db.pendingSmsDao().delete(pending)

    // Loans
    fun getLoans() = db.loanDao().getAll()
    suspend fun getActiveLoans() = db.loanDao().getActiveLoans()
    suspend fun addLoan(loan: LoanEntity) = db.loanDao().insert(loan)
    suspend fun updateLoan(loan: LoanEntity) = db.loanDao().update(loan)
    suspend fun deleteLoan(loan: LoanEntity) = db.loanDao().delete(loan)

    // Categories
    fun getCategoriesByType(type: TxType) = db.categoryDao().getByType(type)
    suspend fun addCategory(name: String, type: TxType) = db.categoryDao().insert(CategoryEntity(name = name, type = type))
    suspend fun renameCategory(category: CategoryEntity, newName: String) = db.categoryDao().update(category.copy(name = newName))

    // Deletes a category safely: transactions still using it are reassigned to the
    // "Other" category (سایر) of the same type so no record is orphaned/miscounted.
    suspend fun deleteCategorySafe(category: CategoryEntity): DeleteCategoryResult {
        val dao = db.categoryDao()
        val count = dao.countTransactionsWithCategory(category.name)
        if (count > 0) {
            val target = defaultOtherFor(category.type)
            if (category.name != target) {
                dao.reassignTransactionsCategory(category.name, target)
            }
        }
        dao.delete(category)
        return DeleteCategoryResult(reassignedCount = count)
    }

    private fun defaultOtherFor(type: TxType): String =
        if (type == TxType.EXPENSE) "سایر" else "سایر"

    data class DeleteCategoryResult(val reassignedCount: Int)

    // Full-data export (used for backup). Fetches everything once.
    suspend fun exportAll(): ExportBundle {
        return ExportBundle(
            transactions = db.transactionDao().getAllOnce(),
            accounts = db.bankAccountDao().getAllOnce(),
            loans = db.loanDao().getAllOnce(),
            categories = db.categoryDao().getAllOnce(),
            smsSenders = db.smsSenderDao().getAllOnce(),
            financialAssets = db.financialAssetDao().getAllOnce(),
            stockAssets = db.stockAssetDao().getAllOnce()
        )
    }

    // Replaces all local data with the contents of an imported backup bundle.
    // IDs are preserved so relationships (e.g. loanId, bankAccountId) survive.
    suspend fun importBundle(bundle: ExportBundle) {
        // Import atomically: a malformed row or database error must not leave the
        // user with an empty or half-restored database.
        db.withTransaction {
            clearAllData()
            bundle.accounts.forEach { db.bankAccountDao().insert(it) }
            bundle.smsSenders.forEach { db.smsSenderDao().insert(it) }
            bundle.categories.forEach { db.categoryDao().insert(it) }
            bundle.loans.forEach { db.loanDao().insert(it) }
            bundle.transactions.forEach { db.transactionDao().insert(it) }
            bundle.financialAssets.forEach { db.financialAssetDao().upsert(it) }
            bundle.stockAssets.forEach { db.stockAssetDao().upsert(it) }
        }
    }

    // Wipes every table so an imported backup fully replaces current data.
    suspend fun clearAllData() {
        db.transactionDao().deleteAll()
        db.loanDao().deleteAll()
        db.smsSenderDao().deleteAll()
        db.categoryDao().deleteAll()
        db.bankAccountDao().deleteAll()
        db.financialAssetDao().deleteAll()
        db.stockAssetDao().deleteAll()
    }

    data class ExportBundle(
        val transactions: List<TransactionEntity>,
        val accounts: List<BankAccountEntity>,
        val loans: List<LoanEntity>,
        val categories: List<CategoryEntity>,
        val smsSenders: List<SmsSenderEntity> = emptyList(),
        val financialAssets: List<FinancialAssetEntity> = emptyList(),
        val stockAssets: List<StockAssetEntity> = emptyList()
    )
}
