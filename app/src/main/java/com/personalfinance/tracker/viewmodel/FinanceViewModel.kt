package com.personalfinance.tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalfinance.tracker.data.*
import com.personalfinance.tracker.util.JalaliCalendar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.personalfinance.tracker.util.CrashLogger
import com.personalfinance.tracker.util.AppStrings
import java.util.Calendar

class FinanceViewModel(private val repo: FinanceRepository) : ViewModel() {

    private val mutationErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = mutationErrors.asSharedFlow()

    private fun mutate(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashLogger.log("database mutation failed", e)
            mutationErrors.emit(AppStrings.transactionSaveFailed)
        }
    }

    suspend fun refreshAccountBalance(accountId: Long, balance: Double) = repo.refreshAccountBalance(accountId, balance)

    val bankAccounts: StateFlow<List<BankAccountEntity>> =
        repo.getBankAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsSenders: StateFlow<List<SmsSenderEntity>> =
        repo.getSmsSenders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionEntity>> =
        repo.getTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSms: StateFlow<List<PendingSmsEntity>> =
        repo.getPendingSms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviewedSms: StateFlow<List<PendingSmsEntity>> =
        repo.getReviewedSms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<LoanEntity>> =
        repo.getLoans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialAssets: StateFlow<List<FinancialAssetEntity>> =
        repo.getFinancialAssets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockAssets: StateFlow<List<StockAssetEntity>> =
        repo.getStockAssets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveFinancialAsset(type: AssetType, quantityGrams: Double) {
        mutate {
            repo.saveFinancialAsset(FinancialAssetEntity(type, quantityGrams.coerceAtLeast(0.0)))
        }
    }

    suspend fun saveStockAsset(asset: StockAssetEntity) {
        require(asset.quantity > 0.0) { "Stock quantity must be positive." }
        require(asset.buyPriceToman > 0.0) { "Stock buy price must be positive." }
        repo.saveStockAsset(asset)
    }

    fun deleteStockAsset(asset: StockAssetEntity) {
        mutate { repo.deleteStockAsset(asset) }
    }

    val expenseCategories: StateFlow<List<CategoryEntity>> =
        repo.getCategoriesByType(TxType.EXPENSE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> =
        repo.getCategoriesByType(TxType.INCOME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Bank accounts ----
    fun addBankAccount(
        bankName: String,
        label: String,
        openingBalance: Double,
        accountLast4: String = "",
        onCreated: (Long) -> Unit = {}
    ) {
        mutate {
            val id = repo.addBankAccount(
                BankAccountEntity(
                    bankName = bankName,
                    accountLabel = label,
                    accountLast4 = accountLast4,
                    balance = openingBalance
                )
            )
            onCreated(id)
        }
    }
    fun deleteBankAccount(account: BankAccountEntity) = mutate { repo.deleteBankAccount(account) }
    fun updateBankAccount(account: BankAccountEntity) = mutate { repo.updateBankAccount(account) }

    // ---- SMS senders (dynamic add) ----
    fun addSmsSender(senderId: String, bankAccountId: Long, label: String) {
        mutate {
            repo.addSmsSender(SmsSenderEntity(senderId = senderId, bankAccountId = bankAccountId, label = label))
        }
    }
    fun deleteSmsSender(sender: SmsSenderEntity) = mutate { repo.deleteSmsSender(sender) }

    // ---- Transactions ----
    suspend fun addTransaction(
        amount: Double,
        type: TxType,
        category: String,
        note: String,
        bankAccountId: Long?,
        dateMillis: Long = System.currentTimeMillis(),
        loanId: Long? = null,
        balanceAfter: Double? = null,
        source: TxSource = TxSource.MANUAL,
        rawSms: String? = null,
        toAccountId: Long? = null,
        smsTimestampMillis: Long? = null
    ): Long =
        repo.addTransaction(
            TransactionEntity(
                amount = amount, type = type, category = category, note = note,
                dateMillis = dateMillis, bankAccountId = bankAccountId, source = source, loanId = loanId,
                balanceAfter = balanceAfter, rawSms = rawSms, toAccountId = toAccountId, smsTimestampMillis = smsTimestampMillis
            )
        )
    fun deleteTransaction(tx: TransactionEntity) = mutate { repo.deleteTransaction(tx) }

    // Prefer the exact original SMS and timestamp for newly saved SMS transactions.
    // The amount/day comparison remains as a compatibility fallback for older rows.
    suspend fun isReconciled(
        accountId: Long,
        amount: Double?,
        dateMillis: Long,
        rawSms: String
    ): Boolean {
        return repo.getTransactionsByAccount(accountId).any { tx ->
            (tx.rawSms == rawSms && (tx.smsTimestampMillis ?: tx.dateMillis) == dateMillis) ||
                (amount != null &&
                    amount > 0.0 &&
                    sameDay(tx.smsTimestampMillis ?: tx.dateMillis, dateMillis) &&
                    kotlin.math.abs(tx.amount - amount) <= 1.0)
        }
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
                ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }

    suspend fun setShareParent(childId: Long, parentId: Long?) = repo.setShareParent(childId, parentId)

    fun updateTransaction(tx: TransactionEntity) = mutate { repo.updateTransaction(tx) }

    // Records a loan repayment as a transaction linked to the loan, and reduces the
    // loan's remaining balance by the paid amount.
    fun payLoan(loan: LoanEntity, amount: Double, bankAccountId: Long? = null, dateMillis: Long = System.currentTimeMillis()) {
        mutate {
            val tx = TransactionEntity(
                amount = amount, type = TxType.EXPENSE, category = "وام", note = loan.name,
                dateMillis = dateMillis, bankAccountId = bankAccountId, source = TxSource.MANUAL, loanId = loan.id
            )
            repo.addTransaction(tx)
        }
    }

    suspend fun getLoanPayments(loanId: Long): List<TransactionEntity> = repo.getLoanPayments(loanId)

    // Total loan repayments made during the current (Jalali) month, so the
    // dashboard can fold them into the expense total / chart.
    suspend fun loanPaymentsThisMonth(): Double {
        val (start, end) = monthRange(0)
        return repo.getLoanPaymentsBetween(start, end).sumOf { it.amount }
    }

    // ---- Pending SMS confirmation ----
    // Confirmed/rejected SMS are kept (status = CHECKED) so the user retains a
    // review history instead of the record vanishing.
    fun confirmPendingSms(
        pending: PendingSmsEntity,
        finalAmount: Double,
        type: TxType,
        category: String,
        note: String,
        finalBalanceAfter: Double? = null,
        toAccountId: Long? = null,
        dateMillis: Long = pending.timestampMillis
    ) {
        mutate {
            val chosenBalanceAfter = if (type == TxType.CARD_TO_CARD) null else finalBalanceAfter ?: pending.parsedBalance
            repo.confirmPendingSms(pending,
                TransactionEntity(
                    amount = finalAmount, type = type, category = category, note = note,
                    dateMillis = dateMillis, bankAccountId = pending.bankAccountId,
                    source = TxSource.SMS, rawSms = pending.rawMessage, smsTimestampMillis = pending.timestampMillis,
                    balanceAfter = chosenBalanceAfter,
                    toAccountId = if (type == TxType.CARD_TO_CARD) toAccountId else null
                )
            )
        }
    }
    fun rejectPendingSms(pending: PendingSmsEntity) {
        mutate { repo.updatePendingSms(pending.copy(status = PendingStatus.CHECKED)) }
    }
    fun deletePendingSms(pending: PendingSmsEntity) = mutate { repo.deletePendingSms(pending) }

    // ---- Loans ----
    fun addLoan(
        name: String,
        principal: Double,
        dueDateMillis: Long,
        installment: Double,
        totalMonths: Int,
        bankAccountId: Long?,
        reminderDaysBefore: Int,
        notes: String
    ) {
        mutate {
            repo.addLoan(
                LoanEntity(
                    name = name, principal = principal, remainingAmount = principal,
                    dueDateMillis = dueDateMillis, installment = installment, totalMonths = totalMonths,
                    bankAccountId = bankAccountId,
                    reminderDaysBefore = reminderDaysBefore, notes = notes
                )
            )
        }
    }
    fun addLoan(
        name: String,
        principal: Double,
        payDayOfMonth: Int,
        installment: Double,
        totalMonths: Int,
        bankAccountId: Long?,
        reminderDaysBefore: Int,
        notes: String
    ) {
        mutate {
            repo.addLoan(
                LoanEntity(
                    name = name, principal = principal, remainingAmount = principal,
                    dueDateMillis = JalaliCalendar.nextDueDateMillis(payDayOfMonth),
                    payDayOfMonth = payDayOfMonth, installment = installment, totalMonths = totalMonths,
                    bankAccountId = bankAccountId,
                    reminderDaysBefore = reminderDaysBefore, notes = notes
                )
            )
        }
    }
    fun markLoanPaid(loan: LoanEntity) = mutate { repo.updateLoan(loan.copy(isPaid = true, remainingAmount = 0.0)) }
    fun updateLoan(loan: LoanEntity) = mutate { repo.updateLoan(loan) }
    fun deleteLoan(loan: LoanEntity) = mutate { repo.deleteLoan(loan) }

    // Remaining months until payoff, derived from the installment so it drops
    // automatically with every payment. Defaults to totalMonths when no installment.
    fun monthsRemaining(loan: LoanEntity, currentMonthAlreadyPaid: Boolean = false): Int {
        val remaining = if (loan.installment > 0.0) {
            kotlin.math.ceil(loan.remainingAmount / loan.installment).toInt().coerceAtLeast(0)
        } else loan.totalMonths
        return (remaining + if (currentMonthAlreadyPaid && remaining > 0) 1 else 0).coerceAtLeast(0)
    }

    // ---- Categories ----
    fun addCategory(name: String, type: TxType) {
        mutate { repo.addCategory(name.trim(), type) }
    }
    fun renameCategory(category: CategoryEntity, newName: String) {
        mutate { repo.renameCategory(category, newName.trim()) }
    }
    suspend fun deleteCategorySafe(category: CategoryEntity): FinanceRepository.DeleteCategoryResult {
        return repo.deleteCategorySafe(category)
    }

    // ---- Export / backup ----
    suspend fun exportAll(): FinanceRepository.ExportBundle = repo.exportAll()
    suspend fun importBundle(bundle: FinanceRepository.ExportBundle) = repo.importBundle(bundle)

    // ---- Reports ----
    suspend fun monthlyIncomeExpense(monthOffset: Int = 0): Pair<Double, Double> {
        val (start, end) = monthRange(monthOffset)
        val income = repo.totalByType(TxType.INCOME, start, end)
        val expense = repo.totalByType(TxType.EXPENSE, start, end)
        return income to expense
    }

    suspend fun categoryBreakdown(monthOffset: Int = 0): List<CategoryTotal> {
        val (start, end) = monthRange(monthOffset)
        return repo.expenseByCategory(start, end)
    }

    // Last `count` months (including current) of income/expense, oldest first.
    // Expense includes loan repayments made that month.
    suspend fun monthlyHistory(count: Int = 6): List<Pair<Double, Double>> {
        return (count - 1 downTo 0).map { offset ->
            val (start, end) = monthRange(-offset)
            val income = repo.totalByType(TxType.INCOME, start, end)
            val expense = repo.totalByType(TxType.EXPENSE, start, end)
            income to expense
        }
    }

    suspend fun balanceHistory(count: Int = 6): List<Double> {
        // Running net-worth at the END of each of the last `count` months (oldest first).
        // Seeded with the current account balance minus the window's net, so the final
        // point matches the live total account balance.
        val ranges = (count - 1 downTo 0).map { offset -> monthRange(-offset) }
        val nets = ranges.map { (start, end) -> repo.netBetween(start, end) }
        val windowNet = nets.sum()
        val seed = repo.totalAccountBalance() - windowNet
        val out = mutableListOf<Double>()
        var running = seed
        for (n in nets) {
            running += n
            out.add(running)
        }
        return out
    }

    suspend fun totalAccountBalance(): Double = repo.totalAccountBalance()

    private fun monthRange(monthOffset: Int): Pair<Long, Long> {
        return JalaliCalendar.jalaliMonthRange(Calendar.getInstance(), monthOffset)
    }
}

class FinanceViewModelFactory(private val repo: FinanceRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FinanceViewModel(repo) as T
    }
}
