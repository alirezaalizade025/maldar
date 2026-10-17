package com.personalfinance.tracker.data

import androidx.room.withTransaction
import com.personalfinance.tracker.sms.SmsParser

class FinanceRepository(private val db: AppDatabase) {

    // Bank accounts
    fun getBankAccounts() = db.bankAccountDao().getAll()
    suspend fun addBankAccount(account: BankAccountEntity) = db.bankAccountDao().insert(account)
    suspend fun updateBankAccount(account: BankAccountEntity) = db.bankAccountDao().update(account)
    suspend fun deleteBankAccount(account: BankAccountEntity) = db.withTransaction {
        db.transactionDao().detachSourceAccount(account.id)
        db.transactionDao().detachDestinationAccount(account.id)
        db.pendingSmsDao().detachAccount(account.id)
        db.loanDao().detachAccount(account.id)
        db.smsSenderDao().deleteByAccount(account.id)
        db.bankAccountDao().delete(account)
    }
    suspend fun getBankAccount(id: Long) = db.bankAccountDao().getById(id)
    suspend fun refreshAccountBalance(id: Long, balance: Double) = db.withTransaction {
        require(balance.isFinite()) { "Invalid account balance." }
        val account = requireNotNull(db.bankAccountDao().getById(id)) { "Account no longer exists." }
        db.bankAccountDao().update(account.copy(balance = balance))
    }

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

    private fun validateTransaction(tx: TransactionEntity) {
        require(tx.amount.isFinite() && tx.amount > 0.0) { "Transaction amount must be positive and finite." }
        require(tx.balanceAfter == null || tx.balanceAfter.isFinite()) { "Invalid remaining balance." }
        if (tx.type == TxType.CARD_TO_CARD) {
            require(tx.bankAccountId != null && tx.toAccountId != null && tx.bankAccountId != tx.toAccountId) {
                "Choose two different accounts for a transfer."
            }
        } else require(tx.toAccountId == null) { "Only transfers have a destination account." }
        require(tx.loanId == null || tx.type == TxType.EXPENSE) { "Loan payments must be expenses." }
    }

    private fun accountEffects(tx: TransactionEntity): Map<Long, Double> = buildMap {
        tx.bankAccountId?.let { put(it, if (tx.type == TxType.INCOME) tx.amount else -tx.amount) }
        if (tx.type == TxType.CARD_TO_CARD) tx.toAccountId?.let { put(it, tx.amount) }
    }

    private suspend fun latestReportedBalance(accountId: Long, excludeId: Long? = null): TransactionEntity? =
        db.transactionDao().getSmsBalancesForAccount(accountId).firstOrNull { tx ->
            // Older app versions also stored calculated SMS balances. Check the original message.
            tx.id != excludeId && (tx.rawSms == null || SmsParser.parse(tx.rawSms).balanceAfter != null)
        }

    private suspend fun adjustAccounts(old: TransactionEntity?, new: TransactionEntity?) {
        val before = old?.let(::accountEffects).orEmpty()
        val after = new?.let(::accountEffects).orEmpty()
        for (id in before.keys + after.keys) {
            val snapshot = latestReportedBalance(id, old?.id)
            fun effect(tx: TransactionEntity?, amounts: Map<Long, Double>): Double =
                if (tx != null && snapshot != null && snapshot.dateMillis > tx.dateMillis) 0.0
                else amounts[id] ?: 0.0
            val delta = effect(new, after) - effect(old, before)
            val account = requireNotNull(db.bankAccountDao().getById(id)) { "The selected bank account no longer exists." }
            // Negative balances preserve the ledger and make deletion the exact inverse.
            db.bankAccountDao().update(account.copy(balance = account.balance + delta))
        }
    }

    private suspend fun adjustLoans(old: TransactionEntity?, new: TransactionEntity?) {
        for (id in listOfNotNull(old?.loanId, new?.loanId).distinct()) {
            val loan = requireNotNull(db.loanDao().getById(id)) { "The selected loan no longer exists." }
            val previousPayment = if (old?.loanId == id) old.amount else 0.0
            val nextPayment = if (new?.loanId == id) new.amount else 0.0
            val remaining = loan.remainingAmount + previousPayment - nextPayment
            require(remaining >= 0.0) { "Payment exceeds the remaining loan balance." }
            db.loanDao().update(loan.copy(remainingAmount = remaining, isPaid = remaining <= 0.0))
        }
    }

    suspend fun addTransaction(tx: TransactionEntity): Long = db.withTransaction {
        validateTransaction(tx)
        adjustAccounts(null, tx)
        adjustLoans(null, tx)
        val accountId = tx.bankAccountId
        val saved = if (accountId != null && tx.type != TxType.CARD_TO_CARD) {
            val account = requireNotNull(db.bankAccountDao().getById(accountId))
            val latest = latestReportedBalance(accountId)
            if (tx.balanceAfter != null && (latest == null || latest.dateMillis <= tx.dateMillis)) {
                // A delayed bank snapshot predates later manual entries; replay those entries.
                val laterEffects = db.transactionDao().getByAccount(accountId)
                    .filter { it.dateMillis > tx.dateMillis }
                    .sumOf { accountEffects(it)[accountId] ?: 0.0 }
                db.bankAccountDao().update(account.copy(balance = tx.balanceAfter + laterEffects))
            }
            // Only bank-reported balances can anchor later reconciliation.
            tx.copy(balanceAfter = tx.balanceAfter ?: account.balance.takeIf { tx.source != TxSource.SMS })
        } else tx.copy(balanceAfter = null)
        db.transactionDao().insert(saved)
    }

    suspend fun deleteTransaction(tx: TransactionEntity) = db.withTransaction {
        // Read the persisted row so repeated clicks and stale UI objects cannot reverse it twice.
        val old = db.transactionDao().getById(tx.id) ?: return@withTransaction
        adjustAccounts(old, null)
        adjustLoans(old, null)
        db.transactionDao().delete(old)
    }

    suspend fun updateTransaction(tx: TransactionEntity) = db.withTransaction {
        val old = requireNotNull(db.transactionDao().getById(tx.id)) { "Transaction no longer exists." }
        validateTransaction(tx)
        adjustAccounts(old, tx)
        adjustLoans(old, tx)
        // Snapshot edits describe history; only changes to the transaction's effect alter live balances.
        db.transactionDao().update(tx)
    }

    suspend fun confirmPendingSms(pending: PendingSmsEntity, tx: TransactionEntity) = db.withTransaction {
        val current = db.pendingSmsDao().getById(pending.id) ?: return@withTransaction
        if (current.status != PendingStatus.PENDING) return@withTransaction
        addTransaction(tx)
        db.pendingSmsDao().update(current.copy(status = PendingStatus.CHECKED,
            parsedAmount = tx.amount, parsedType = tx.type, bankAccountId = tx.bankAccountId))
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
    suspend fun deleteLoan(loan: LoanEntity) = db.withTransaction {
        db.transactionDao().detachLoan(loan.id)
        db.loanDao().delete(loan)
    }

    // Categories
    fun getCategoriesByType(type: TxType) = db.categoryDao().getByType(type)
    suspend fun addCategory(name: String, type: TxType) = db.categoryDao().insert(CategoryEntity(name = name, type = type))
    suspend fun renameCategory(category: CategoryEntity, newName: String) = db.withTransaction {
        require(newName.isNotBlank()) { "Category name is empty." }
        db.categoryDao().reassignTransactionsCategory(category.name, newName, category.type)
        db.categoryDao().update(category.copy(name = newName))
    }

    // Deletes a category safely: transactions still using it are reassigned to the
    // "Other" category (سایر) of the same type so no record is orphaned/miscounted.
    suspend fun deleteCategorySafe(category: CategoryEntity): DeleteCategoryResult = db.withTransaction {
        val dao = db.categoryDao()
        val count = dao.countTransactionsWithCategory(category.name, category.type)
        if (count > 0) {
            val target = "سایر"
            if (category.name != target) {
                dao.reassignTransactionsCategory(category.name, target, category.type)
            }
        }
        dao.delete(category)
        DeleteCategoryResult(reassignedCount = count)
    }

    data class DeleteCategoryResult(val reassignedCount: Int)

    // Full-data export (used for backup). Fetches everything once.
    suspend fun exportAll(): ExportBundle = db.withTransaction {
        ExportBundle(
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
        validateBundle(bundle)
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

    private fun validateBundle(bundle: ExportBundle) {
        fun validIds(ids: List<Long>) = ids.all { it > 0 } && ids.distinct().size == ids.size
        require(validIds(bundle.transactions.map { it.id }) && validIds(bundle.accounts.map { it.id }) &&
            validIds(bundle.loans.map { it.id }) && validIds(bundle.categories.map { it.id }) &&
            validIds(bundle.smsSenders.map { it.id })) { "Backup contains invalid or duplicate IDs." }
        require(bundle.transactions.all { it.amount.isFinite() && it.amount > 0.0 &&
            (it.balanceAfter == null || it.balanceAfter.isFinite()) }) { "Invalid transaction in backup." }
        require(bundle.accounts.all { it.balance.isFinite() }) { "Invalid account balance in backup." }
        require(bundle.loans.all { it.principal.isFinite() && it.principal >= 0.0 &&
            it.remainingAmount.isFinite() && it.remainingAmount >= 0.0 &&
            it.installment.isFinite() && it.installment >= 0.0 }) { "Invalid loan in backup." }
        require(bundle.financialAssets.all { it.quantityGrams.isFinite() && it.quantityGrams >= 0.0 }) {
            "Invalid asset in backup."
        }
        require(bundle.stockAssets.all { it.quantity.isFinite() && it.quantity > 0.0 &&
            it.buyPriceToman.isFinite() && it.buyPriceToman > 0.0 &&
            (it.lastPriceToman == null || it.lastPriceToman.isFinite() && it.lastPriceToman >= 0.0) }) {
            "Invalid stock in backup."
        }
    }

    // Wipes every table so an imported backup fully replaces current data.
    suspend fun clearAllData() = db.withTransaction {
        db.pendingSmsDao().deleteAll()
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
