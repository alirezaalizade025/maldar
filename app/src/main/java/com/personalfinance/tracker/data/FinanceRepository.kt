package com.personalfinance.tracker.data

class FinanceRepository(private val db: AppDatabase) {

    // Bank accounts
    fun getBankAccounts() = db.bankAccountDao().getAll()
    suspend fun addBankAccount(account: BankAccountEntity) = db.bankAccountDao().insert(account)
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

    suspend fun addTransaction(tx: TransactionEntity) {
        db.transactionDao().insert(tx)
        // keep bank account balance in sync
        if (tx.bankAccountId != null) {
            val delta = if (tx.type == TxType.INCOME) tx.amount else -tx.amount
            db.bankAccountDao().adjustBalance(tx.bankAccountId, delta)
        }
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        db.transactionDao().delete(tx)
        if (tx.bankAccountId != null) {
            val delta = if (tx.type == TxType.INCOME) -tx.amount else tx.amount
            db.bankAccountDao().adjustBalance(tx.bankAccountId, delta)
        }
    }

    suspend fun totalByType(type: TxType, start: Long, end: Long): Double =
        db.transactionDao().sumByTypeBetween(type, start, end) ?: 0.0

    suspend fun expenseByCategory(start: Long, end: Long): List<CategoryTotal> =
        db.transactionDao().expenseByCategoryBetween(start, end)

    // Pending SMS (awaiting user confirmation)
    fun getPendingSms() = db.pendingSmsDao().getPending()
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
    suspend fun deleteCategory(category: CategoryEntity) = db.categoryDao().delete(category)
}
