package com.personalfinance.tracker.viewmodel

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.personalfinance.tracker.data.*
import com.personalfinance.tracker.util.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AssetsViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    private lateinit var store: ViewModelStore
    private val selected = StockSearchResult("123", "نماد", "نمونه", "")
    private val stock = StockAssetEntity("123", "نماد", "نمونه", 10.0, 5.0, 7.0, 100)

    @Before fun setup() {
        AppContextProvider.init(RuntimeEnvironment.getApplication())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FinanceRepository(db)
        store = ViewModelStore()
    }

    @After fun close() {
        store.clear()
        shadowOf(Looper.getMainLooper()).idle()
        db.close()
    }

    private fun model(
        metals: suspend () -> MetalPrices = { error("Unexpected metal request") },
        search: suspend (String) -> List<StockSearchResult> = { error("Unexpected search") },
        quote: suspend (String) -> StockQuote = { error("Unexpected quote request") }
    ): AssetsViewModel = AssetsViewModel(repo, metals, search, quote).also { store.put("assets", it) }

    private suspend fun awaitState(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(10)
        }
    }

    @Test fun offlineSave_preservesCachedQuoteAndPersistsEditedHolding() = runBlocking {
        repo.saveStockAsset(stock)
        val model = model(quote = { throw java.io.IOException("offline fixture") })
        model.selectedStock = selected
        model.quantityText = "12"
        model.buyPriceText = "6"
        model.saveStock(listOf(stock))
        awaitState { !model.stockSaving }
        val saved = repo.exportAll().stockAssets.single()
        assertEquals(12.0, saved.quantity, 0.0)
        assertEquals(6.0, saved.buyPriceToman, 0.0)
        assertEquals(7.0, saved.lastPriceToman!!, 0.0)
        assertEquals(100L, saved.lastPriceUpdatedAt)
        assertNull(model.stockError)
        assertNull(model.selectedStock)
        assertEquals(AppStrings.stockSaved, model.stockMessage)
    }

    @Test fun rejectedSave_preservesInputAndCanBeRetried() = runBlocking {
        val model = model()
        model.selectedStock = selected
        model.quantityText = "12"
        model.buyPriceText = "6"
        model.selectedQuote = StockQuote("123", Double.NaN, null, 200)
        model.saveStock(emptyList())
        awaitState { !model.stockSaving }
        assertTrue(repo.exportAll().stockAssets.isEmpty())
        assertEquals(AppStrings.operationFailed, model.stockError)
        assertEquals("12", model.quantityText)
        assertEquals("6", model.buyPriceText)
        assertEquals(selected, model.selectedStock)
        model.selectedQuote = StockQuote("123", 8.0, null, 200)
        model.saveStock(emptyList())
        awaitState { !model.stockSaving }
        assertEquals(8.0, repo.exportAll().stockAssets.single().lastPriceToman!!, 0.0)
        assertNull(model.stockError)
    }

    @Test fun nonFiniteInputs_areRejectedWithoutPersisting() = runBlocking {
        val model = model()
        model.selectedStock = selected
        model.quantityText = "Infinity"
        model.buyPriceText = "6"
        model.saveStock(emptyList())
        assertFalse(model.stockSaving)
        assertEquals(AppStrings.stockInvalidInput, model.stockError)
        assertTrue(repo.exportAll().stockAssets.isEmpty())
    }

    @Test fun quoteRefresh_doesNotOverwriteConcurrentQuantityEdit() = runBlocking {
        repo.saveStockAsset(stock)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<StockQuote>()
        val model = model(quote = { started.complete(Unit); response.await() })
        model.refreshStocks(listOf(stock))
        awaitState { started.isCompleted }
        repo.saveStockAsset(stock.copy(quantity = 20.0, buyPriceToman = 9.0))
        response.complete(StockQuote("123", 8.0, null, 200))
        awaitState { !model.stockRefreshing }
        val saved = repo.exportAll().stockAssets.single()
        assertEquals(20.0, saved.quantity, 0.0)
        assertEquals(9.0, saved.buyPriceToman, 0.0)
        assertEquals(8.0, saved.lastPriceToman!!, 0.0)
    }

    @Test fun clearingViewModel_cancelsRefreshWithoutWritingAQuote() = runBlocking {
        repo.saveStockAsset(stock)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<StockQuote>()
        val model = model(quote = { started.complete(Unit); response.await() })
        model.refreshStocks(listOf(stock))
        awaitState { started.isCompleted }
        store.clear()
        awaitState { !model.stockRefreshing }
        response.complete(StockQuote("123", 99.0, null, 200))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(stock, repo.exportAll().stockAssets.single())
    }

    @Test fun invalidMetalQuantity_preservesBothHoldings() = runBlocking {
        repo.saveMetalAssets(2.0, 3.0)
        val before = repo.exportAll().financialAssets
        val model = model()
        model.refreshMetals(9.0, Double.NaN)
        awaitState { !model.metalLoading }
        assertNotNull(model.metalError)
        assertEquals(before, repo.exportAll().financialAssets)
    }

    @Test fun searchFailure_resetsLoadingAndAllowsRetry() = runBlocking {
        var offline = true
        val model = model(search = {
            if (offline) throw java.io.IOException("offline fixture")
            listOf(selected)
        })
        model.searchStocks("نماد")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        awaitState { !model.searchLoading }
        assertEquals(AppStrings.stockSearchFailed, model.searchError)
        assertTrue(model.searchResults.isEmpty())
        offline = false
        model.searchStocks("نماد")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        awaitState { !model.searchLoading }
        assertNull(model.searchError)
        assertEquals(listOf(selected), model.searchResults)
    }
}
