package com.personalfinance.tracker.data

class FinanceRepository(private val db: AppDatabase) {

    // Bank accounts
    fun getBankAccounts() = db.bankAccountDao().getAll()
    suspend fun addBankAccount(account: BankAccountEntity) = db.bankAccountDao().insert(account)
    suspend fun updateBankAccount(account: BankAccountEntity) = db.bankAccountDao().update(account)
    suspend fun deleteBankAccount(account: BankAccountEntity) = db.bankAccountDao().delete(account)
    suspend fun getBankAccount(id: Long) = db.bankAccountDao().getById(id)

    // SMS senders (dynamic list of numbers/ids to watch)
    fun getSmsSenders() = db.smsSenderDao().getAll()
    suspend fun getSmsSendersOnce() = db.smsSenderDao().getAllOnce()
    suspend fun addSmsSender(sender: SmsSenderEntity) = db.smsSenderDao().insert(sender)
    suspend fun deleteSmsSender(sender: SmsSenderEntity) = db.smsSenderDao().delete(sender)

    // Transactions (expenses + incomes)
    fun getTransactions() = db.transactionDao().getAll()
    fun getTransactionsBetween(start: Long, end: Long) = db.transactionDao().getBetween(start, end)

    // Inserts a transaction and, when it is linked to a bank account, records the
    // account's remaining balance right after it (balanceAfter) on the transaction
    // itself, and updates the account's current remained.
    suspend fun addTransaction(tx: TransactionEntity) {
        if (tx.bankAccountId != null) {
            val account = db.bankAccountDao().getById(tx.bankAccountId)
            if (account != null) {
                val delta = if (tx.type == TxType.INCOME) tx.amount else -tx.amount
                val remained = (account.balance + delta).coerceAtLeast(0.0)
                db.bankAccountDao().update(account.copy(balance = remained))
                db.transactionDao().insert(tx.copy(balanceAfter = remained))
                return
            }
        }
        db.transactionDao().insert(tx)
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        db.transactionDao().delete(tx)
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

    // Total remained = sum of each account's latest remained balance.
    // The remained for an account is read from the balanceAfter snapshot on its
    // most recent transaction; if the account has no transactions yet, its stored
    // balance (opening remained) is used.
    suspend fun totalAccountBalance(): Double {
        val accounts = db.bankAccountDao().getAllOnce()
        return accounts.sumOf { account ->
            val latest = db.transactionDao().latestBalanceForAccount(account.id)
            latest?.balanceAfter ?: account.balance
        }
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
            categories = db.categoryDao().getAllOnce()
        )
    }

    data class ExportBundle(
        val transactions: List<TransactionEntity>,
        val accounts: List<BankAccountEntity>,
        val loans: List<LoanEntity>,
        val categories: List<CategoryEntity>
    )
}
