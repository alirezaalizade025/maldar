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
import java.util.Calendar

class FinanceViewModel(private val repo: FinanceRepository) : ViewModel() {

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

    val expenseCategories: StateFlow<List<CategoryEntity>> =
        repo.getCategoriesByType(TxType.EXPENSE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> =
        repo.getCategoriesByType(TxType.INCOME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Bank accounts ----
    fun addBankAccount(bankName: String, label: String, last4: String, openingBalance: Double, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.addBankAccount(
                BankAccountEntity(bankName = bankName, accountLabel = label, accountLast4 = last4, balance = openingBalance)
            )
            onCreated(id)
        }
    }
    fun deleteBankAccount(account: BankAccountEntity) = viewModelScope.launch { repo.deleteBankAccount(account) }
    fun updateBankAccount(account: BankAccountEntity) = viewModelScope.launch { repo.updateBankAccount(account) }

    // ---- SMS senders (dynamic add) ----
    fun addSmsSender(senderId: String, bankAccountId: Long, label: String) {
        viewModelScope.launch {
            repo.addSmsSender(SmsSenderEntity(senderId = senderId, bankAccountId = bankAccountId, label = label))
        }
    }
    fun deleteSmsSender(sender: SmsSenderEntity) = viewModelScope.launch { repo.deleteSmsSender(sender) }

    // ---- Transactions ----
    fun addTransaction(amount: Double, type: TxType, category: String, note: String, bankAccountId: Long?, dateMillis: Long = System.currentTimeMillis(), loanId: Long? = null) {
        viewModelScope.launch {
            repo.addTransaction(
                TransactionEntity(
                    amount = amount, type = type, category = category, note = note,
                    dateMillis = dateMillis, bankAccountId = bankAccountId, source = TxSource.MANUAL, loanId = loanId
                )
            )
        }
    }
    fun deleteTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(tx) }

    fun updateTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.updateTransaction(tx) }

    // Records a loan repayment as a transaction linked to the loan, and reduces the
    // loan's remaining balance by the paid amount.
    fun payLoan(loan: LoanEntity, amount: Double, dateMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                amount = amount, type = TxType.EXPENSE, category = "وام", note = loan.name,
                dateMillis = dateMillis, bankAccountId = null, source = TxSource.MANUAL, loanId = loan.id
            )
            repo.addTransaction(tx)
            val remaining = (loan.remainingAmount - amount).coerceAtLeast(0.0)
            val paid = remaining <= 0.0
            repo.updateLoan(loan.copy(remainingAmount = remaining, isPaid = paid))
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
    fun confirmPendingSms(pending: PendingSmsEntity, finalAmount: Double, type: TxType, category: String, note: String) {
        viewModelScope.launch {
            repo.addTransaction(
                TransactionEntity(
                    amount = finalAmount, type = type, category = category, note = note,
                    dateMillis = pending.timestampMillis, bankAccountId = pending.bankAccountId,
                    source = TxSource.SMS, rawSms = pending.rawMessage
                )
            )
            repo.updatePendingSms(pending.copy(status = PendingStatus.CHECKED, parsedType = type, parsedAmount = finalAmount))
        }
    }
    fun rejectPendingSms(pending: PendingSmsEntity) {
        viewModelScope.launch { repo.updatePendingSms(pending.copy(status = PendingStatus.CHECKED)) }
    }
    fun deletePendingSms(pending: PendingSmsEntity) = viewModelScope.launch { repo.deletePendingSms(pending) }

    // ---- Loans ----
    fun addLoan(name: String, principal: Double, dueDateMillis: Long, installment: Double, totalMonths: Int, reminderDaysBefore: Int, notes: String) {
        viewModelScope.launch {
            repo.addLoan(
                LoanEntity(
                    name = name, principal = principal, remainingAmount = principal,
                    dueDateMillis = dueDateMillis, installment = installment, totalMonths = totalMonths,
                    reminderDaysBefore = reminderDaysBefore, notes = notes
                )
            )
        }
    }
    fun addLoan(name: String, principal: Double, payDayOfMonth: Int, installment: Double, totalMonths: Int, reminderDaysBefore: Int, notes: String) {
        viewModelScope.launch {
            repo.addLoan(
                LoanEntity(
                    name = name, principal = principal, remainingAmount = principal,
                    dueDateMillis = JalaliCalendar.nextDueDateMillis(payDayOfMonth),
                    payDayOfMonth = payDayOfMonth, installment = installment, totalMonths = totalMonths,
                    reminderDaysBefore = reminderDaysBefore, notes = notes
                )
            )
        }
    }
    fun markLoanPaid(loan: LoanEntity) = viewModelScope.launch { repo.updateLoan(loan.copy(isPaid = true, remainingAmount = 0.0)) }
    fun deleteLoan(loan: LoanEntity) = viewModelScope.launch { repo.deleteLoan(loan) }

    // Remaining months until payoff, derived from the installment so it drops
    // automatically with every payment. Defaults to totalMonths when no installment.
    fun monthsRemaining(loan: LoanEntity): Int {
        return if (loan.installment > 0.0) {
            kotlin.math.ceil(loan.remainingAmount / loan.installment).toInt().coerceAtLeast(0)
        } else loan.totalMonths
    }

    // ---- Categories ----
    fun addCategory(name: String, type: TxType) {
        viewModelScope.launch { repo.addCategory(name.trim(), type) }
    }
    fun renameCategory(category: CategoryEntity, newName: String) {
        viewModelScope.launch { repo.renameCategory(category, newName.trim()) }
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
            val expense = repo.totalByType(TxType.EXPENSE, start, end) + repo.getLoanPaymentsBetween(start, end).sumOf { it.amount }
            income to expense
        }
    }

    // Running net-worth at the END of each of the last `count` months (oldest first).
    // Seeded with the current account balance minus the window's net, so the final
    // point matches the live total account balance.
    suspend fun balanceHistory(count: Int = 6): List<Double> {
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
