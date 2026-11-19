package com.personalfinance.tracker.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalfinance.tracker.data.*
import com.personalfinance.tracker.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class AssetsViewModel(
    private val repo: FinanceRepository,
    private val fetchMetals: suspend () -> MetalPrices = MetalPriceService::fetch,
    private val search: suspend (String) -> List<StockSearchResult> = TsetmcStockService::search,
    private val quote: suspend (String) -> StockQuote = TsetmcStockService::quote
) : ViewModel() {
    var metalPrices by mutableStateOf<MetalPrices?>(null)
        private set
    var metalLoading by mutableStateOf(false)
        private set
    var metalError by mutableStateOf<String?>(null)
        private set
    var searchText by mutableStateOf("")
    var searchResults by mutableStateOf<List<StockSearchResult>>(emptyList())
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var selectedStock by mutableStateOf<StockSearchResult?>(null)
    var selectedQuote by mutableStateOf<StockQuote?>(null)
    var quantityText by mutableStateOf("")
    var buyPriceText by mutableStateOf("")
    var stockSaving by mutableStateOf(false)
        private set
    var stockRefreshing by mutableStateOf(false)
        private set
    var stockMessage by mutableStateOf<String?>(null)
    var stockError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null
    private var quoteJob: Job? = null

    fun refreshMetals(gold: Double? = null, silver: Double? = null) {
        if (metalLoading) return
        metalLoading = true
        metalError = null
        viewModelScope.launch {
            try {
                if (gold != null && silver != null) repo.saveMetalAssets(gold, silver)
                metalPrices = fetchMetals()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                CrashLogger.log("Metal save/refresh failed", e)
                metalError = AppStrings.assetPriceFailed
            } finally { metalLoading = false }
        }
    }

    fun refreshStocks(stocks: List<StockAssetEntity>) {
        if (stocks.isEmpty() || stockRefreshing || stockSaving) return
        stockRefreshing = true
        stockError = null
        viewModelScope.launch {
            try {
                for (stock in stocks) {
                    try {
                        val price = quote(stock.instrumentCode)
                        // Update only the quote, so a concurrent quantity edit cannot be overwritten.
                        repo.updateStockQuote(stock.instrumentCode, price.lastPriceToman, price.fetchedAtMillis)
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        CrashLogger.log("Stock quote refresh failed", e)
                        stockError = AppStrings.assetPriceFailed
                    }
                }
            } finally { stockRefreshing = false }
        }
    }

    fun selectStock(result: StockSearchResult, existing: StockAssetEntity? = null) {
        quoteJob?.cancel()
        searchJob?.cancel()
        selectedStock = result
        searchText = result.symbol
        searchResults = emptyList()
        searchLoading = false
        searchError = null
        stockMessage = null
        stockError = null
        quantityText = existing?.quantity?.let(Money::input) ?: ""
        buyPriceText = existing?.buyPriceToman?.let(Money::input) ?: ""
        selectedQuote = existing?.lastPriceToman?.let {
            StockQuote(existing.instrumentCode, it, null, existing.lastPriceUpdatedAt ?: 0L)
        }
        quoteJob = viewModelScope.launch {
            try {
                val price = quote(result.instrumentCode)
                if (selectedStock?.instrumentCode == result.instrumentCode) selectedQuote = price
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                CrashLogger.log("Selected stock quote failed", e)
                if (selectedStock?.instrumentCode == result.instrumentCode) stockError = AppStrings.priceUnavailable
            }
        }
    }

    fun searchStocks(query: String) {
        searchJob?.cancel()
        searchError = null
        searchResults = emptyList()
        val text = query.trim()
        searchLoading = text.length >= 2 && selectedStock?.symbol != text
        if (!searchLoading) return
        searchJob = viewModelScope.launch {
            try {
                delay(450)
                searchResults = search(text)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                CrashLogger.log("Stock search failed", e)
                searchError = AppStrings.stockSearchFailed
            } finally { if (isActive) searchLoading = false }
        }
    }

    fun saveStock(stocks: List<StockAssetEntity>) {
        val selected = selectedStock ?: return
        val quantity = quantityText.toDoubleOrNull()
        val buyPrice = buyPriceText.toDoubleOrNull()
        if (quantity == null || !quantity.isFinite() || quantity <= 0 || buyPrice == null || !buyPrice.isFinite() || buyPrice <= 0) {
            stockError = AppStrings.stockInvalidInput
            return
        }
        if (stockSaving || stockRefreshing) return
        stockSaving = true
        stockError = null
        stockMessage = null
        viewModelScope.launch {
            try {
                val existing = stocks.firstOrNull { it.instrumentCode == selected.instrumentCode }
                val price = selectedQuote?.takeIf { it.instrumentCode == selected.instrumentCode } ?: try {
                    quote(selected.instrumentCode)
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { CrashLogger.log("Quote before save failed", e); null }
                repo.saveStockAsset(StockAssetEntity(selected.instrumentCode, selected.symbol, selected.name,
                    quantity, buyPrice, price?.lastPriceToman ?: existing?.lastPriceToman,
                    price?.fetchedAtMillis ?: existing?.lastPriceUpdatedAt))
                stockMessage = AppStrings.stockSaved
                selectedStock = null
                selectedQuote = null
                searchText = ""
                quantityText = ""
                buyPriceText = ""
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                CrashLogger.log("Stock save failed", e)
                stockError = AppStrings.operationFailed
            } finally { stockSaving = false }
        }
    }

    fun deleteStock(stock: StockAssetEntity, onSuccess: () -> Unit) {
        if (stockSaving || stockRefreshing) return
        stockSaving = true
        viewModelScope.launch {
            try { repo.deleteStockAsset(stock); onSuccess()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                CrashLogger.log("Stock delete failed", e)
                stockError = AppStrings.operationFailed
            } finally { stockSaving = false }
        }
    }
}
