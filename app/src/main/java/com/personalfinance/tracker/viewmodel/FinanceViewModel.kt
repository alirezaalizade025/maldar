package com.personalfinance.tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalfinance.tracker.data.*
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

    val loans: StateFlow<List<LoanEntity>> =
        repo.getLoans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<CategoryEntity>> =
        repo.getCategoriesByType(TxType.EXPENSE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> =
        repo.getCategoriesByType(TxType.INCOME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Bank accounts ----
    fun addBankAccount(bankName: String, label: String, last4: String, openingBalance: Double) {
        viewModelScope.launch {
            repo.addBankAccount(
                BankAccountEntity(bankName = bankName, accountLabel = label, accountLast4 = last4, balance = openingBalance)
            )
        }
    }
    fun deleteBankAccount(account: BankAccountEntity) = viewModelScope.launch { repo.deleteBankAccount(account) }

    // ---- SMS senders (dynamic add) ----
    fun addSmsSender(senderId: String, bankAccountId: Long, label: String) {
        viewModelScope.launch {
            repo.addSmsSender(SmsSenderEntity(senderId = senderId, bankAccountId = bankAccountId, label = label))
        }
    }
    fun deleteSmsSender(sender: SmsSenderEntity) = viewModelScope.launch { repo.deleteSmsSender(sender) }

    // ---- Transactions ----
    fun addTransaction(amount: Double, type: TxType, category: String, note: String, bankAccountId: Long?, dateMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repo.addTransaction(
                TransactionEntity(
                    amount = amount, type = type, category = category, note = note,
                    dateMillis = dateMillis, bankAccountId = bankAccountId, source = TxSource.MANUAL
                )
            )
        }
    }
    fun deleteTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(tx) }

    // ---- Pending SMS confirmation ----
    fun confirmPendingSms(pending: PendingSmsEntity, finalAmount: Double, type: TxType, category: String, note: String) {
        viewModelScope.launch {
            repo.addTransaction(
                TransactionEntity(
                    amount = finalAmount, type = type, category = category, note = note,
                    dateMillis = pending.timestampMillis, bankAccountId = pending.bankAccountId,
                    source = TxSource.SMS, rawSms = pending.rawMessage
                )
            )
            repo.updatePendingSms(pending.copy(status = PendingStatus.CONFIRMED))
        }
    }
    fun rejectPendingSms(pending: PendingSmsEntity) {
        viewModelScope.launch { repo.updatePendingSms(pending.copy(status = PendingStatus.REJECTED)) }
    }

    // ---- Loans ----
    fun addLoan(name: String, principal: Double, dueDateMillis: Long, reminderDaysBefore: Int, notes: String) {
        viewModelScope.launch {
            repo.addLoan(
                LoanEntity(
                    name = name, principal = principal, remainingAmount = principal,
                    dueDateMillis = dueDateMillis, reminderDaysBefore = reminderDaysBefore, notes = notes
                )
            )
        }
    }
    fun markLoanPaid(loan: LoanEntity) = viewModelScope.launch { repo.updateLoan(loan.copy(isPaid = true, remainingAmount = 0.0)) }
    fun deleteLoan(loan: LoanEntity) = viewModelScope.launch { repo.deleteLoan(loan) }

    // ---- Categories ----
    fun addCategory(name: String, type: TxType) {
        viewModelScope.launch { repo.addCategory(name.trim(), type) }
    }
    fun renameCategory(category: CategoryEntity, newName: String) {
        viewModelScope.launch { repo.renameCategory(category, newName.trim()) }
    }
    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch { repo.deleteCategory(category) }

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
    suspend fun monthlyHistory(count: Int = 6): List<Pair<Double, Double>> {
        return (count - 1 downTo 0).map { offset ->
            val (start, end) = monthRange(-offset)
            val income = repo.totalByType(TxType.INCOME, start, end)
            val expense = repo.totalByType(TxType.EXPENSE, start, end)
            income to expense
        }
    }

    private fun monthRange(monthOffset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthOffset)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1
        return start to end
    }
}

class FinanceViewModelFactory(private val repo: FinanceRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FinanceViewModel(repo) as T
    }
}
