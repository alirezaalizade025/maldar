package com.personalfinance.tracker.data

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FinanceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FinanceRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun account(balance: Double) = repo.addBankAccount(BankAccountEntity(
        bankName = "bank", accountLabel = "test", accountLast4 = "", balance = balance))
    private fun expense(accountId: Long?, amount: Double = 30.0) = TransactionEntity(
        amount = amount, type = TxType.EXPENSE, category = "food", dateMillis = 100, bankAccountId = accountId)
    private suspend fun balance(id: Long) = repo.getBankAccount(id)!!.balance

    @Test fun editAmountAndAccount_reversesOldEffectAndAppliesNewEffect() = runBlocking {
        val a = account(100.0); val b = account(200.0)
        val id = repo.addTransaction(expense(a))
        repo.updateTransaction(expense(b, 50.0).copy(id = id))
        assertEquals(100.0, balance(a), 0.0)
        assertEquals(150.0, balance(b), 0.0)
    }
    @Test fun snapshotOnlyEdit_doesNotOverwriteLiveBalance() = runBlocking {
        val a = account(100.0)
        val id = repo.addTransaction(expense(a))
        val saved = repo.exportAll().transactions.single()
        repo.updateTransaction(saved.copy(id = id, balanceAfter = 999.0))
        assertEquals(70.0, balance(a), 0.0)
    }
    @Test fun deleteTwice_reversesOnlyPersistedTransactionOnce() = runBlocking {
        val a = account(100.0)
        val tx = expense(a).copy(id = repo.addTransaction(expense(a)))
        repo.deleteTransaction(tx); repo.deleteTransaction(tx)
        assertEquals(100.0, balance(a), 0.0)
    }
    @Test fun transferAndDelete_conserveMoneyEvenWhenSourceWouldBeNegative() = runBlocking {
        val a = account(20.0); val b = account(0.0)
        val tx = expense(a, 30.0).copy(type = TxType.CARD_TO_CARD, toAccountId = b)
        val id = repo.addTransaction(tx)
        assertEquals(-10.0, balance(a), 0.0)
        assertEquals(30.0, balance(b), 0.0)
        repo.deleteTransaction(tx.copy(id = id))
        assertEquals(20.0, balance(a), 0.0)
        assertEquals(0.0, balance(b), 0.0)
    }
    @Test fun linkedLoanPayment_addEditDelete_updatesRemaining() = runBlocking {
        val loan = LoanEntity(name = "loan", principal = 100.0, remainingAmount = 100.0, dueDateMillis = 100)
        val loanId = repo.addLoan(loan)
        val tx = expense(null, 30.0).copy(loanId = loanId)
        val id = repo.addTransaction(tx)
        assertEquals(70.0, repo.exportAll().loans.single().remainingAmount, 0.0)
        repo.updateTransaction(tx.copy(id = id, amount = 50.0))
        assertEquals(50.0, repo.exportAll().loans.single().remainingAmount, 0.0)
        repo.deleteTransaction(tx.copy(id = id))
        assertEquals(100.0, repo.exportAll().loans.single().remainingAmount, 0.0)
    }
    @Test fun deletingExpenseCategory_doesNotChangeIncomeCategory() = runBlocking {
        repo.addTransaction(expense(null).copy(category = "shared"))
        repo.addTransaction(expense(null).copy(category = "shared", type = TxType.INCOME))
        val category = CategoryEntity(name = "shared", type = TxType.EXPENSE)
        val id = db.categoryDao().insert(category)
        repo.deleteCategorySafe(category.copy(id = id))
        assertEquals("shared", repo.exportAll().transactions.single { it.type == TxType.INCOME }.category)
        assertEquals("سایر", repo.exportAll().transactions.single { it.type == TxType.EXPENSE }.category)
    }
    @Test fun renameCategory_updatesExistingTransactions() = runBlocking {
        repo.addTransaction(expense(null))
        val c = CategoryEntity(name = "food", type = TxType.EXPENSE)
        val id = db.categoryDao().insert(c)
        repo.renameCategory(c.copy(id = id), "groceries")
        assertEquals("groceries", repo.exportAll().transactions.single().category)
    }
    @Test fun failedTransfer_doesNotDebitSource() = runBlocking {
        val a = account(100.0)
        try {
            repo.addTransaction(expense(a).copy(type = TxType.CARD_TO_CARD, toAccountId = 999))
            fail("Missing destination must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(100.0, balance(a), 0.0)
        assertTrue(repo.exportAll().transactions.isEmpty())
    }
    @Test fun deletingAccount_detachesLinksAndRemovesSenders() = runBlocking {
        val a = account(100.0)
        repo.addTransaction(expense(a))
        repo.addSmsSender(SmsSenderEntity(senderId = "BANK", bankAccountId = a))
        repo.deleteBankAccount(repo.getBankAccount(a)!!)
        assertNull(repo.exportAll().transactions.single().bankAccountId)
        assertTrue(repo.getSmsSendersOnce().isEmpty())
    }
    @Test fun invalidAmount_isRejectedWithoutChangingBalance() = runBlocking {
        val a = account(100.0)
        for (amount in listOf(-5.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try { repo.addTransaction(expense(a, amount)); fail("Invalid amount must fail") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(100.0, balance(a), 0.0)
        assertTrue(repo.exportAll().transactions.isEmpty())
    }

    @Test fun confirmationTwice_createsOnlyOneTransaction() = runBlocking {
        val a = account(100.0)
        val pending = PendingSmsEntity(rawMessage = "bank message", sender = "BANK", parsedAmount = 30.0,
            parsedType = TxType.EXPENSE, timestampMillis = 100, bankAccountId = a)
        val id = repo.addPendingSms(pending)
        repo.confirmPendingSms(pending.copy(id = id), expense(a))
        repo.confirmPendingSms(pending.copy(id = id), expense(a))
        assertEquals(70.0, balance(a), 0.0)
        assertEquals(1, repo.exportAll().transactions.size)
    }
    @Test fun failedLoanPayment_rollsBackAccountDebit() = runBlocking {
        val a = account(100.0)
        try { repo.addTransaction(expense(a).copy(loanId = 999)); fail("Missing loan must fail") }
        catch (_: IllegalArgumentException) { }
        assertEquals(100.0, balance(a), 0.0)
        assertTrue(repo.exportAll().transactions.isEmpty())
    }
    @Test fun historicalSmsSnapshot_doesNotReplaceNewerLiveBalance() = runBlocking {
        val a = account(1000.0)
        repo.addTransaction(expense(a, 50.0).copy(source = TxSource.SMS, dateMillis = 200, balanceAfter = 900.0))
        repo.addTransaction(expense(a, 50.0).copy(source = TxSource.SMS, dateMillis = 100, balanceAfter = 950.0))
        assertEquals(900.0, balance(a), 0.0)
        assertEquals(950.0, repo.exportAll().transactions.last().balanceAfter!!, 0.0)
    }
    @Test fun restoringBackup_clearsPendingMessagesFromPreviousDatabase() = runBlocking {
        val p = PendingSmsEntity(rawMessage = "old", sender = "BANK", parsedAmount = 1.0,
            parsedType = TxType.EXPENSE, timestampMillis = 100, bankAccountId = null)
        val id = repo.addPendingSms(p)
        repo.importBundle(FinanceRepository.ExportBundle(emptyList(), emptyList(), emptyList(), emptyList()))
        assertNull(db.pendingSmsDao().getById(id))
    }

    @Test fun invalidBackup_doesNotReplaceExistingData() = runBlocking {
        val a = account(100.0)
        val invalid = FinanceRepository.ExportBundle(listOf(expense(null, 0.0).copy(id = 1)),
            emptyList(), emptyList(), emptyList())
        try { repo.importBundle(invalid); fail("Invalid backup must fail") }
        catch (_: IllegalArgumentException) { }
        assertEquals(100.0, balance(a), 0.0)
    }

    @Test fun delayedSmsSnapshot_replaysLaterManualTransactions() = runBlocking {
        val a = account(1000.0)
        repo.addTransaction(expense(a, 50.0).copy(source = TxSource.SMS, dateMillis = 100, balanceAfter = 1000.0))
        repo.addTransaction(expense(a, 100.0).copy(dateMillis = 300))
        repo.addTransaction(expense(a, 50.0).copy(source = TxSource.SMS, dateMillis = 200, balanceAfter = 800.0))
        assertEquals(700.0, balance(a), 0.0)
    }
    @Test fun smsWithoutBankBalance_doesNotHideEarlierTransactionEdits() = runBlocking {
        val a = account(100.0)
        val tx = expense(a, 10.0).copy(dateMillis = 100)
        val id = repo.addTransaction(tx)
        repo.addTransaction(expense(a, 20.0).copy(source = TxSource.SMS, dateMillis = 200))
        repo.updateTransaction(tx.copy(id = id, amount = 15.0))
        assertEquals(65.0, balance(a), 0.0)
    }

    @Test fun editingOldSmsDatePastNewerSnapshot_doesNotReplayBankMovement() = runBlocking {
        val a = account(100.0)
        val id = repo.addTransaction(expense(a, 20.0).copy(source = TxSource.SMS, dateMillis = 100, balanceAfter = 80.0))
        repo.addTransaction(expense(a, 10.0).copy(source = TxSource.SMS, dateMillis = 200, balanceAfter = 70.0))
        val old = repo.exportAll().transactions.single { it.id == id }
        repo.updateTransaction(old.copy(dateMillis = 300, smsTimestampMillis = null))
        assertEquals(70.0, balance(a), 0.0)
        assertEquals(100L, repo.exportAll().transactions.single { it.id == id }.smsTimestampMillis)
    }

    @Test fun editingSmsDate_doesNotReverseItsOwnAmount() = runBlocking {
        val a = account(100.0)
        val tx = expense(a, 20.0).copy(source = TxSource.SMS, dateMillis = 200, balanceAfter = 80.0)
        val id = repo.addTransaction(tx)
        repo.updateTransaction(tx.copy(id = id, dateMillis = 100))
        assertEquals(80.0, balance(a), 0.0)
    }
    @Test fun legacyComputedSmsBalance_isNotTreatedAsBankReported() = runBlocking {
        val a = account(100.0)
        val tx = expense(a, 10.0)
        val id = repo.addTransaction(tx)
        db.transactionDao().insert(expense(a, 20.0).copy(source = TxSource.SMS, dateMillis = 200,
            balanceAfter = 70.0, rawSms = "برداشت 200 ریال"))
        repo.refreshAccountBalance(a, 70.0)
        repo.updateTransaction(tx.copy(id = id, amount = 15.0))
        assertEquals(65.0, balance(a), 0.0)
    }

    @Test fun smsTransfer_keepsSelectedDateAndDestination() = runBlocking {
        val a = account(100.0); val b = account(10.0)
        val p = PendingSmsEntity(rawMessage = "bank message", sender = "BANK", parsedAmount = 30.0,
            parsedType = TxType.EXPENSE, timestampMillis = 100, bankAccountId = a)
        val id = repo.addPendingSms(p)
        repo.confirmPendingSms(p.copy(id = id), expense(a).copy(type = TxType.CARD_TO_CARD,
            toAccountId = b, dateMillis = 500, source = TxSource.SMS, rawSms = p.rawMessage))
        val saved = repo.exportAll().transactions.single()
        assertEquals(TxType.CARD_TO_CARD, saved.type)
        assertEquals(b, saved.toAccountId)
        assertEquals(500L, saved.dateMillis)
        assertEquals(100L, saved.smsTimestampMillis)
        assertEquals(70.0, balance(a), 0.0)
        assertEquals(40.0, balance(b), 0.0)
    }

    @Test fun shareFractionalRepayments_canExactlyCoverOriginalExpense() = runBlocking {
        val parent = repo.addTransaction(expense(null, 3.3))
        val a = repo.addTransaction(expense(null, 1.1).copy(type = TxType.INCOME))
        val b = repo.addTransaction(expense(null, 2.2).copy(type = TxType.INCOME))
        repo.setShareParent(a, parent)
        repo.setShareParent(b, parent)
        assertEquals(0.0, repo.totalByType(TxType.EXPENSE, 0, 1000), 0.0)
    }

    @Test fun shareLinkUnlinkAndDelete_preserveBankMovements() = runBlocking {
        val a = account(2000.0)
        val parent = repo.addTransaction(expense(a, 1000.0))
        val child = repo.addTransaction(expense(a, 200.0).copy(type = TxType.INCOME))
        repo.setShareParent(child, parent)
        assertEquals(1200.0, balance(a), 0.0)
        assertEquals(800.0, repo.totalByType(TxType.EXPENSE, 0, 1000), 0.0)
        assertEquals(0.0, repo.totalByType(TxType.INCOME, 0, 1000), 0.0)
        repo.setShareParent(child, null)
        assertEquals(1000.0, repo.totalByType(TxType.EXPENSE, 0, 1000), 0.0)
        repo.setShareParent(child, parent)
        repo.deleteTransaction(repo.exportAll().transactions.single { it.id == parent })
        assertNull(repo.exportAll().transactions.single().parentTransactionId)
        assertEquals(2200.0, balance(a), 0.0)
    }
    @Test fun shareRejectsOverpaymentAndInvalidEditsAtomically() = runBlocking {
        val a = account(2000.0)
        val parent = repo.addTransaction(expense(a, 100.0))
        val child = repo.addTransaction(expense(a, 80.0).copy(type = TxType.INCOME))
        val other = repo.addTransaction(expense(a, 30.0).copy(type = TxType.INCOME))
        repo.setShareParent(child, parent)
        try { repo.setShareParent(other, parent); fail("Overpayment must fail") } catch (_: IllegalArgumentException) {}
        val old = repo.exportAll().transactions.single { it.id == parent }
        try { repo.updateTransaction(old.copy(amount = 50.0)); fail("Cannot shrink below repayments") } catch (_: IllegalArgumentException) {}
        try { repo.setShareParent(parent, child); fail("Invalid types must fail") } catch (_: IllegalArgumentException) {}
        assertEquals(2010.0, balance(a), 0.0)
        assertEquals(20.0, repo.totalByType(TxType.EXPENSE, 0, 1000), 0.0)
    }

    @Test fun shareChildEditsAndDeletion_updateReportingAndActualCashSeparately() = runBlocking {
        val a = account(1000.0)
        val parent = repo.addTransaction(expense(a, 500.0))
        val child = repo.addTransaction(expense(a, 100.0).copy(type = TxType.INCOME))
        repo.setShareParent(child, parent)
        val original = repo.exportAll().transactions.single { it.id == child }
        repo.updateTransaction(original.copy(amount = 200.0))
        assertEquals(700.0, balance(a), 0.0)
        assertEquals(300.0, repo.totalByType(TxType.EXPENSE, 0, 1000), 0.0)
        assertEquals(-300.0, repo.netBetween(0, 1000), 0.0)
        repo.deleteTransaction(original)
        assertEquals(500.0, balance(a), 0.0)
        assertEquals(500.0, repo.totalByType(TxType.EXPENSE, 0, 1000), 0.0)
    }
    @Test fun shareBackup_invalidLinkDoesNotEraseData_andValidLinksRestore() = runBlocking {
        val a = account(1000.0)
        val parent = repo.addTransaction(expense(a, 500.0))
        val child = repo.addTransaction(expense(a, 100.0).copy(type = TxType.INCOME))
        repo.setShareParent(child, parent)
        val backup = repo.exportAll()
        try {
            repo.importBundle(backup.copy(transactions = backup.transactions.filter { it.id == child }))
            fail("Dangling parent must fail")
        } catch (_: IllegalArgumentException) {}
        assertEquals(2, repo.exportAll().transactions.size)
        repo.importBundle(backup)
        assertEquals(parent, repo.exportAll().transactions.single { it.id == child }.parentTransactionId)
        assertEquals(600.0, balance(a), 0.0)
    }

}
