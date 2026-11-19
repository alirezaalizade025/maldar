package com.personalfinance.tracker.data

import android.os.Looper
import androidx.room.Room
import com.personalfinance.tracker.sms.BankMessageProcessor
import com.personalfinance.tracker.util.*
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RefactorIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    private val message = "برداشت:1,000\nحساب:1234\nمانده:9,000"

    @Before fun setup() {
        AppContextProvider.init(RuntimeEnvironment.getApplication())
        Settings.restore(BackupSettings())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FinanceRepository(db)
    }

    @After fun cleanup() { db.close(); Settings.restore(BackupSettings()) }

    private suspend fun bank(): Long {
        return repo.addBankAccount(BankAccountEntity(bankName = "bank", accountLabel = "account", accountLast4 = "1234"), listOf("BANK"))
    }

    @Test fun smsAndExpandedNotification_produceOnePendingRecord() = runBlocking {
        bank()
        val processor = BankMessageProcessor(db)
        assertNotNull(processor.capture("BANK", message, 100_000))
        assertNull(processor.capture("bank.app", "BANK\n$message", 101_000))
        assertEquals(1, repo.exportAll().pendingSms.size)
        assertNotNull(processor.capture("BANK", message, 110_000))
    }

    @Test fun simultaneousDeliveries_insertOnlyOnce() = runBlocking {
        bank()
        val processor = BankMessageProcessor(db)
        val results = awaitAll(async(Dispatchers.IO) { processor.capture("BANK", message, 100_000) },
            async(Dispatchers.IO) { processor.capture("BANK", message, 100_000) })
        assertEquals(1, results.count { it != null })
    }

    @Test fun duplicateAfterConfirmationAndPendingDeletion_isStillSuppressed() = runBlocking {
        val account = bank()
        val processor = BankMessageProcessor(db)
        val pending = requireNotNull(processor.capture("BANK", message, 100_000))
        repo.confirmPendingSms(pending, TransactionEntity(amount = 100.0, type = TxType.EXPENSE,
            category = "food", dateMillis = 900_000, bankAccountId = account, source = TxSource.SMS, rawSms = message))
        repo.deletePendingSms(pending)
        assertNull(processor.capture("BANK", message, 101_000))
    }

    @Test fun differentReferenceWithSameAmount_isNotADuplicate() = runBlocking {
        bank()
        val processor = BankMessageProcessor(db)
        assertNotNull(processor.capture("BANK", "$message\nپیگیری:111", 100_000))
        assertNotNull(processor.capture("BANK", "$message\nپیگیری:222", 101_000))
    }

    @Test fun unknownSenderAndPasswords_areNotCaptured() = runBlocking {
        bank()
        val processor = BankMessageProcessor(db)
        assertNull(processor.capture("UNKNOWN", message, 100_000))
        assertNull(processor.capture("BANK", "رمز پویا کارت 1234: 999999", 100_000))
    }

    @Test fun jsonRoundTrip_restoresReviewHistoryAndSettings() = runBlocking {
        bank()
        val pending = requireNotNull(BankMessageProcessor(db).capture("BANK", message, 100_000))
        repo.updatePendingSms(pending.copy(status = PendingStatus.CHECKED))
        val settings = BackupSettings(true, 8, 15, false, 150_000, setOf("BANK|100000"))
        Settings.restore(settings)
        val backup = DataExport.fromJson(DataExport.toJson(repo.exportAll()))
        assertEquals(settings, backup.settings)
        Settings.restore(BackupSettings())
        repo.importBundle(backup)
        assertEquals(settings, Settings.snapshot())
        assertEquals(PendingStatus.CHECKED, repo.exportAll().pendingSms.single().status)
    }

    @Test fun legacyBackup_remainsReadableAndUnknownVersionsAreRejected() = runBlocking {
        val json = JSONObject(DataExport.toJson(repo.exportAll()))
        json.remove("formatVersion"); json.remove("settings"); json.remove("pendingSms")
        assertTrue(DataExport.fromJson(json.toString()).pendingSms.isEmpty())
        json.put("formatVersion", 999)
        try { DataExport.fromJson(json.toString()); fail("Future format must fail") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun invalidBackupSettingsAndPendingReferences_preserveExistingData() = runBlocking {
        bank()
        val original = repo.exportAll()
        val pending = PendingSmsEntity(id = 1, rawMessage = message, sender = "BANK", parsedAmount = 100.0,
            parsedType = TxType.EXPENSE, timestampMillis = 100_000, bankAccountId = 999)
        for (bad in listOf(original.copy(pendingSms = listOf(pending)), original.copy(settings = BackupSettings(dailyReminderHour = 30)))) {
            try { repo.importBundle(bad); fail("Invalid backup must fail") } catch (_: IllegalArgumentException) { }
            assertEquals(original, repo.exportAll())
        }
    }

    @Test fun quoteRefresh_doesNotOverwriteQuantityOrRecreateDeletedStock() = runBlocking {
        val stock = StockAssetEntity("123", "symbol", "name", 10.0, 5.0)
        repo.saveStockAsset(stock)
        repo.saveStockAsset(stock.copy(quantity = 20.0))
        repo.updateStockQuote("123", 7.0, 100)
        assertEquals(20.0, repo.exportAll().stockAssets.single().quantity, 0.0)
        repo.deleteStockAsset(stock)
        repo.updateStockQuote("123", 8.0, 200)
        assertTrue(repo.exportAll().stockAssets.isEmpty())
    }

    @Test fun failedMutation_doesNotCallSuccessAndAllowsRetry() = runBlocking {
        val model = FinanceViewModel(repo)
        var successes = 0
        val error = async(Dispatchers.Unconfined) { model.errors.first() }
        model.updateTransaction(TransactionEntity(id = 999, amount = 1.0, type = TxType.EXPENSE,
            category = "food", dateMillis = 100)) { successes++ }
        withTimeout(5_000) { while (model.saving.value) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
        assertEquals(AppStrings.transactionSaveFailed, error.await())
        assertEquals(0, successes)
        model.addBankAccount("bank", "account", 0.0) { successes++ }
        withTimeout(5_000) { while (model.saving.value) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
        assertEquals(1, successes)
    }

    @Test fun independentMutations_areNotSilentlyDropped() = runBlocking {
        val model = FinanceViewModel(repo)
        var successes = 0
        model.addBankAccount("bank", "first", 1.0) { successes++ }
        model.addBankAccount("bank", "second", 2.0) { successes++ }
        withTimeout(5_000) { while (model.saving.value) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
        assertEquals(2, successes)
        assertEquals(2, repo.exportAll().accounts.size)
    }

    @Test fun melliMessage_captureConfirmAndRestore_preserveAccountAndBalance() = runBlocking {
        val account = repo.addBankAccount(BankAccountEntity(bankName = "ملی", accountLabel = "ملی",
            accountLast4 = "5000", balance = 1_000_000.0), listOf("BANK"))
        val body = "بانك ملي ايران\nبرداشت:5,870,000-\nحساب:85000\nمانده:5,913,480\n0817-18:29"
        val pending = requireNotNull(BankMessageProcessor(db).capture("BANK", body, 100_000))
        assertEquals(account, pending.bankAccountId)
        assertEquals(TxType.EXPENSE, pending.parsedType)
        assertEquals(587_000.0, pending.parsedAmount!!, 0.0)
        assertEquals(591_348.0, pending.parsedBalance!!, 0.0)
        val model = FinanceViewModel(repo)
        var saved = false
        model.confirmPendingSms(pending, requireNotNull(pending.parsedAmount), TxType.EXPENSE, "خرید", "fixture",
            onSuccess = { saved = true })
        withTimeout(5_000) { while (model.saving.value) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
        assertTrue(saved)
        assertEquals(591_348.0, repo.getBankAccount(account)!!.balance, 0.0)
        val backup = DataExport.fromJson(DataExport.toJson(repo.exportAll()))
        repo.importBundle(backup)
        val restored = repo.exportAll()
        assertEquals(account, restored.transactions.single().bankAccountId)
        assertEquals(body, restored.transactions.single().rawSms)
        assertEquals(PendingStatus.CHECKED, restored.pendingSms.single().status)
        assertEquals(591_348.0, restored.accounts.single().balance, 0.0)
    }
}
