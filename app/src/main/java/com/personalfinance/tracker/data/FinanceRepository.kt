package com.personalfinance.tracker.data

import androidx.room.withTransaction
import com.personalfinance.tracker.sms.SmsParser
import com.personalfinance.tracker.util.BackupSettings
import com.personalfinance.tracker.util.Settings

class FinanceRepository(private val db: AppDatabase) {

    // Bank accounts
    fun getBankAccounts() = db.bankAccountDao().getAll()
    suspend fun addBankAccount(account: BankAccountEntity, senderIds: List<String> = emptyList()) = db.withTransaction {
        require(account.balance.isFinite()) { "Invalid account balance." }
        val id = db.bankAccountDao().insert(account)
        senderIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
            db.smsSenderDao().insert(SmsSenderEntity(senderId = it, bankAccountId = id))
        }
        id
    }
    suspend fun updateBankAccount(account: BankAccountEntity) {
        require(account.balance.isFinite()) { "Invalid account balance." }
        db.bankAccountDao().update(account)
    }
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
    fun getStockAssets() = db.stockAssetDao().getAll()
    suspend fun saveStockAsset(asset: StockAssetEntity) {
        require(asset.quantity.isFinite() && asset.quantity > 0 && asset.buyPriceToman.isFinite() && asset.buyPriceToman > 0 &&
            (asset.lastPriceToman == null || asset.lastPriceToman.isFinite() && asset.lastPriceToman >= 0)) { "Invalid stock asset." }
        db.stockAssetDao().upsert(asset)
    }
    suspend fun updateStockQuote(code: String, price: Double, timestamp: Long) {
        require(price.isFinite() && price >= 0) { "Invalid stock quote." }
        db.stockAssetDao().updateQuote(code, price, timestamp)
    }
    suspend fun saveMetalAssets(gold: Double, silver: Double) = db.withTransaction {
        require(gold.isFinite() && gold >= 0 && silver.isFinite() && silver >= 0) { "Invalid asset quantity." }
        db.financialAssetDao().upsert(FinancialAssetEntity(AssetType.GOLD_18K, gold))
        db.financialAssetDao().upsert(FinancialAssetEntity(AssetType.SILVER_999, silver))
    }
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
        db.transactionDao().getSmsBalancesForAccount(accountId).filter { tx ->
            // Older app versions also stored calculated SMS balances. Check the original message.
            tx.id != excludeId && (tx.rawSms == null || SmsParser.parse(tx.rawSms).balanceAfter != null)
        }.maxWithOrNull(compareBy<TransactionEntity> { it.bankMovementMillis }.thenBy { it.id })

    private suspend fun adjustAccounts(old: TransactionEntity?, new: TransactionEntity?) {
        val before = old?.let(::accountEffects).orEmpty()
        val after = new?.let(::accountEffects).orEmpty()
        for (id in before.keys + after.keys) {
            val snapshot = latestReportedBalance(id, old?.id)
            fun effect(tx: TransactionEntity?, amounts: Map<Long, Double>): Double =
                if (tx != null && snapshot != null && snapshot.bankMovementMillis > tx.bankMovementMillis) 0.0
                else amounts[id] ?: 0.0
            val delta = DecimalMoney.subtract(effect(new, after), effect(old, before))
            val account = requireNotNull(db.bankAccountDao().getById(id)) { "The selected bank account no longer exists." }
            // Negative balances preserve the ledger and make deletion the exact inverse.
            db.bankAccountDao().update(account.copy(balance = DecimalMoney.add(account.balance, delta)))
        }
    }

    private suspend fun adjustLoans(old: TransactionEntity?, new: TransactionEntity?) {
        for (id in listOfNotNull(old?.loanId, new?.loanId).distinct()) {
            val loan = requireNotNull(db.loanDao().getById(id)) { "The selected loan no longer exists." }
            val previousPayment = if (old?.loanId == id) old.amount else 0.0
            val nextPayment = if (new?.loanId == id) new.amount else 0.0
            val remaining = DecimalMoney.subtract(DecimalMoney.add(loan.remainingAmount, previousPayment), nextPayment)
            require(remaining >= 0.0) { "Payment exceeds the remaining loan balance." }
            db.loanDao().update(loan.copy(remainingAmount = remaining, isPaid = remaining <= 0.0))
        }
    }

    suspend fun addTransaction(tx: TransactionEntity): Long = db.withTransaction {
        validateTransaction(tx)
        validateShareLinks(db.transactionDao().getShareFamily(tx.id, tx.parentTransactionId).filter { it.id != tx.id } + tx)
        adjustAccounts(null, tx)
        adjustLoans(null, tx)
        val accountId = tx.bankAccountId
        val saved = if (accountId != null && tx.type != TxType.CARD_TO_CARD) {
            val account = requireNotNull(db.bankAccountDao().getById(accountId))
            val latest = latestReportedBalance(accountId)
            if (tx.balanceAfter != null && (latest == null || latest.bankMovementMillis <= tx.bankMovementMillis)) {
                // A delayed bank snapshot predates later manual entries; replay those entries.
                val laterEffects = DecimalMoney.sum(db.transactionDao().getAccountMovementsAfter(accountId, tx.bankMovementMillis)) {
                    accountEffects(it)[accountId] ?: 0.0
                }
                db.bankAccountDao().update(account.copy(balance = DecimalMoney.add(tx.balanceAfter, laterEffects)))
            }
            // Only bank-reported balances can anchor later reconciliation.
            tx.copy(balanceAfter = tx.balanceAfter ?: account.balance.takeIf { tx.source != TxSource.SMS })
        } else tx.copy(balanceAfter = null)
        db.transactionDao().insert(saved.copy(smsTimestampMillis =
            if (saved.source == TxSource.SMS) saved.smsTimestampMillis ?: saved.dateMillis else null))
    }

    suspend fun deleteTransaction(tx: TransactionEntity) = db.withTransaction {
        // Read the persisted row so repeated clicks and stale UI objects cannot reverse it twice.
        val old = db.transactionDao().getById(tx.id) ?: return@withTransaction
        adjustAccounts(old, null)
        adjustLoans(old, null)
        db.transactionDao().detachShareParent(old.id)
        db.transactionDao().delete(old)
    }

    suspend fun updateTransaction(updated: TransactionEntity) = db.withTransaction {
        val old = requireNotNull(db.transactionDao().getById(updated.id)) { "Transaction no longer exists." }
        val tx = updated.copy(smsTimestampMillis = old.smsTimestampMillis)
        validateTransaction(tx)
        validateShareLinks(db.transactionDao().getShareFamily(tx.id, tx.parentTransactionId).filter { it.id != tx.id } + tx)
        adjustAccounts(old, tx)
        adjustLoans(old, tx)
        // Snapshot edits describe history; only changes to the transaction's effect alter live balances.
        db.transactionDao().update(tx)
    }

    suspend fun setShareParent(childId: Long, parentId: Long?) = db.withTransaction {
        val child = requireNotNull(db.transactionDao().getById(childId)) { "تراکنش پیدا نشد." }
        val updated = child.copy(parentTransactionId = parentId)
        validateShareLinks(db.transactionDao().getShareFamily(childId, parentId).filter { it.id != childId } + updated)
        // Linking changes reporting only, never account balances or bank snapshots.
        db.transactionDao().update(updated)
    }

    suspend fun confirmPendingSms(pending: PendingSmsEntity, tx: TransactionEntity) = db.withTransaction {
        val current = db.pendingSmsDao().getById(pending.id) ?: return@withTransaction
        if (current.status != PendingStatus.PENDING) return@withTransaction
        addTransaction(tx.copy(smsTimestampMillis = current.timestampMillis))
        db.pendingSmsDao().update(current.copy(status = PendingStatus.CHECKED,
            parsedAmount = tx.amount, parsedType = tx.type, bankAccountId = tx.bankAccountId))
    }

    suspend fun getLoanPayments(loanId: Long): List<TransactionEntity> =
        db.transactionDao().getPaymentsForLoan(loanId)

    suspend fun getLoanPaymentsBetween(start: Long, end: Long): List<TransactionEntity> =
        db.transactionDao().getPaymentsBetween(start, end)

    suspend fun reportBetween(start: Long, end: Long): TransactionReport {
        val now = System.currentTimeMillis()
        return transactionReport(db.transactionDao().getReportRows(start, end, now), start, end, asOfMillis = now)
    }

    suspend fun totalByType(type: TxType, start: Long, end: Long): Double = reportBetween(start, end).let {
        when (type) { TxType.INCOME -> it.income; TxType.EXPENSE -> it.expense; TxType.CARD_TO_CARD -> it.transfers }
    }

    // Cash flow uses actual movements; share repayments belong to their payment date.
    suspend fun netBetween(start: Long, end: Long): Double {
        val rows = db.transactionDao().getReportRows(start, end, end).filter { it.dateMillis in start..end }
        return DecimalMoney.sum(rows) {
            when (it.type) { TxType.INCOME -> it.amount; TxType.EXPENSE -> -it.amount; TxType.CARD_TO_CARD -> 0.0 }
        }
    }

    suspend fun totalAccountBalance(): Double = DecimalMoney.sum(db.bankAccountDao().getAllOnce()) { it.balance }

    suspend fun expenseByCategory(start: Long, end: Long): List<CategoryTotal> = reportBetween(start, end).categories

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
            stockAssets = db.stockAssetDao().getAllOnce(),
            pendingSms = db.pendingSmsDao().getAllOnce(),
            settings = Settings.snapshot()
        )
    }

    // Replaces all local data with the contents of an imported backup bundle.
    // IDs are preserved so relationships (e.g. loanId, bankAccountId) survive.
    suspend fun importBundle(bundle: ExportBundle) {
        validateBundle(bundle)
        val previousSettings = Settings.snapshot()
        // Import atomically: a malformed row or database error must not leave the
        // user with an empty or half-restored database.
        try { db.withTransaction {
            clearAllData()
            bundle.accounts.forEach { db.bankAccountDao().insert(it) }
            bundle.smsSenders.forEach { db.smsSenderDao().insert(it) }
            bundle.categories.forEach { db.categoryDao().insert(it) }
            bundle.loans.forEach { db.loanDao().insert(it) }
            bundle.transactions.forEach { db.transactionDao().insert(it) }
            bundle.financialAssets.forEach { db.financialAssetDao().upsert(it) }
            bundle.stockAssets.forEach { db.stockAssetDao().upsert(it) }
            bundle.pendingSms.forEach { db.pendingSmsDao().insert(it) }
            Settings.restore(bundle.settings ?: previousSettings.copy(lastSmsCheckMillis = 0, ignoredSms = emptySet()))
        } } catch (error: Exception) {
            try { Settings.restore(previousSettings) } catch (restoreError: Exception) { error.addSuppressed(restoreError) }
            throw error
        }
    }

    private fun validateBundle(bundle: ExportBundle) {
        fun validIds(ids: List<Long>) = ids.all { it > 0 } && ids.distinct().size == ids.size
        require(validIds(bundle.transactions.map { it.id }) && validIds(bundle.accounts.map { it.id }) &&
            validIds(bundle.loans.map { it.id }) && validIds(bundle.categories.map { it.id }) &&
            validIds(bundle.smsSenders.map { it.id })) { "Backup contains invalid or duplicate IDs." }
        require(validIds(bundle.pendingSms.map { it.id })) { "Backup contains invalid pending message IDs." }
        bundle.settings?.validate()
        val accounts = bundle.accounts.map { it.id }.toSet()
        val loans = bundle.loans.map { it.id }.toSet()
        fun accountExists(id: Long?) = id == null || id in accounts
        require(bundle.transactions.all { accountExists(it.bankAccountId) && accountExists(it.toAccountId) &&
            (it.loanId == null || it.loanId in loans) } &&
            bundle.loans.all { accountExists(it.bankAccountId) } &&
            bundle.smsSenders.all { it.bankAccountId in accounts } &&
            bundle.pendingSms.all { accountExists(it.bankAccountId) }) { "Backup contains missing account or loan references." }
        bundle.transactions.forEach {
            // Deleted accounts leave historical transfers detached; validate remaining links.
            require(it.toAccountId == null || it.type == TxType.CARD_TO_CARD) { "Invalid transfer in backup." }
            require(it.type != TxType.CARD_TO_CARD || it.bankAccountId == null || it.toAccountId == null || it.bankAccountId != it.toAccountId) { "Invalid transfer in backup." }
            require(it.loanId == null || it.type == TxType.EXPENSE) { "Invalid loan payment in backup." }
        }
        require(bundle.pendingSms.all { row ->
            listOfNotNull(row.parsedAmount, row.parsedBalance).all { it.isFinite() } &&
                (row.parsedAmount == null || row.parsedAmount > 0)
        }) { "Invalid pending message in backup." }
        require(bundle.financialAssets.map { it.type }.distinct().size == bundle.financialAssets.size &&
            bundle.stockAssets.map { it.instrumentCode }.distinct().size == bundle.stockAssets.size) { "Duplicate assets in backup." }
        require(bundle.transactions.all { it.amount.isFinite() && it.amount > 0.0 &&
            (it.balanceAfter == null || it.balanceAfter.isFinite()) }) { "Invalid transaction in backup." }
        validateShareLinks(bundle.transactions)
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
        val stockAssets: List<StockAssetEntity> = emptyList(),
        val pendingSms: List<PendingSmsEntity> = emptyList(),
        val settings: BackupSettings? = null
    )
}
