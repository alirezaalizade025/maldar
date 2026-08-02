package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.AssetType
import com.personalfinance.tracker.data.StockAssetEntity
import com.personalfinance.tracker.ui.theme.AppCard
import com.personalfinance.tracker.util.*
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FinancialAssetsScreen(viewModel: FinanceViewModel) {
    val metals by viewModel.financialAssets.collectAsState()
    val stocks by viewModel.stockAssets.collectAsState()
    var goldAmount by remember(metals) {
        mutableStateOf(metals.firstOrNull { it.type == AssetType.GOLD_18K }?.quantityGrams?.let(Money::input) ?: "")
    }
    var silverAmount by remember(metals) {
        mutableStateOf(metals.firstOrNull { it.type == AssetType.SILVER_999 }?.quantityGrams?.let(Money::input) ?: "")
    }
    var metalPrices by remember { mutableStateOf<MetalPrices?>(null) }
    var metalLoading by remember { mutableStateOf(false) }
    var metalError by remember { mutableStateOf<String?>(null) }

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var selectedStock by remember { mutableStateOf<StockSearchResult?>(null) }
    var selectedQuote by remember { mutableStateOf<StockQuote?>(null) }
    var quantityText by remember { mutableStateOf("") }
    var buyPriceText by remember { mutableStateOf("") }
    var stockSaving by remember { mutableStateOf(false) }
    var stockRefreshing by remember { mutableStateOf(false) }
    var stockMessage by remember { mutableStateOf<String?>(null) }
    var stockError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshMetals() {
        metalLoading = true
        metalError = null
        scope.launch {
            runCatching { MetalPriceService.fetch() }
                .onSuccess { metalPrices = it }
                .onFailure { metalError = it.message ?: AppStrings.assetPriceFailed }
            metalLoading = false
        }
    }

    fun refreshStocks(snapshot: List<StockAssetEntity> = stocks) {
        if (snapshot.isEmpty() || stockRefreshing) return
        stockRefreshing = true
        stockError = null
        scope.launch {
            var firstFailure: Throwable? = null
            snapshot.forEach { stock ->
                runCatching { TsetmcStockService.quote(stock.instrumentCode) }
                    .onSuccess { quote ->
                        viewModel.saveStockAsset(
                            stock.copy(
                                lastPriceToman = quote.lastPriceToman,
                                lastPriceUpdatedAt = quote.fetchedAtMillis
                            )
                        )
                    }
                    .onFailure {
                        if (it is CancellationException) throw it
                        if (firstFailure == null) firstFailure = it
                    }
            }
            firstFailure?.let {
                CrashLogger.log("assets: TSETMC stock refresh failed", it)
                stockError = it.message ?: AppStrings.assetPriceFailed
            }
            stockRefreshing = false
        }
    }

    fun selectStock(result: StockSearchResult, existing: StockAssetEntity? = null) {
        selectedStock = result
        searchText = result.symbol
        searchResults = emptyList()
        searchError = null
        stockMessage = null
        stockError = null
        quantityText = existing?.quantity?.let(Money::input) ?: ""
        buyPriceText = existing?.buyPriceToman?.let(Money::input) ?: ""
        selectedQuote = existing?.lastPriceToman?.let {
            StockQuote(
                instrumentCode = existing.instrumentCode,
                lastPriceToman = it,
                closingPriceToman = null,
                fetchedAtMillis = existing.lastPriceUpdatedAt ?: 0L
            )
        }
        scope.launch {
            val requestedCode = result.instrumentCode
            runCatching { TsetmcStockService.quote(result.instrumentCode) }
                .onSuccess {
                    if (selectedStock?.instrumentCode == requestedCode) selectedQuote = it
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (selectedStock?.instrumentCode == requestedCode) {
                        CrashLogger.log("assets: selected TSETMC quote failed", it)
                        stockError = it.message ?: AppStrings.priceUnavailable
                    }
                }
        }
    }

    LaunchedEffect(Unit) { refreshMetals() }
    val stockCodes = stocks.map { it.instrumentCode }
    LaunchedEffect(stockCodes) {
        if (stockCodes.isNotEmpty()) refreshStocks(stocks)
    }

    LaunchedEffect(searchText, selectedStock?.instrumentCode) {
        val query = searchText.trim()
        if (query.length < 2 || selectedStock?.symbol == query) {
            searchResults = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }
        searchLoading = true
        searchError = null
        try {
            delay(450)
            searchResults = TsetmcStockService.search(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            searchResults = emptyList()
            searchError = error.message ?: AppStrings.stockSearchFailed
        } finally {
            searchLoading = false
        }
    }

    val goldValue = (goldAmount.toDoubleOrNull() ?: 0.0) * (metalPrices?.gold18TomanPerGram ?: 0.0)
    val silverValue = (silverAmount.toDoubleOrNull() ?: 0.0) * (metalPrices?.silver999TomanPerGram ?: 0.0)
    val stockValue = stocks.sumOf { it.quantity * (it.lastPriceToman ?: 0.0) }
    val pricedStocks = stocks.filter { it.lastPriceToman != null }
    val stockProfit = pricedStocks.sumOf { stock ->
        stock.quantity * ((stock.lastPriceToman ?: 0.0) - stock.buyPriceToman)
    }
    val pricedCost = pricedStocks.sumOf { it.quantity * it.buyPriceToman }
    val profitPercent = if (pricedCost > 0.0) stockProfit / pricedCost * 100.0 else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(AppStrings.financialAssets, style = MaterialTheme.typography.headlineMedium) }
        item { Text(AppStrings.assetsHint, style = MaterialTheme.typography.bodySmall) }

        if (metalPrices != null || stocks.any { it.lastPriceToman != null }) {
            item {
                AppCard(
                    filled = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(AppStrings.assetsTotal, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${Money.format2(goldValue + silverValue + stockValue)} ${AppStrings.moneyUnit}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            AssetEditor(AppStrings.gold18, goldAmount, metalPrices?.gold18TomanPerGram) { goldAmount = it }
        }
        item {
            AssetEditor(AppStrings.silver999, silverAmount, metalPrices?.silver999TomanPerGram) { silverAmount = it }
        }
        item {
            Button(
                onClick = {
                    viewModel.saveFinancialAsset(AssetType.GOLD_18K, goldAmount.toDoubleOrNull() ?: 0.0)
                    viewModel.saveFinancialAsset(AssetType.SILVER_999, silverAmount.toDoubleOrNull() ?: 0.0)
                    refreshMetals()
                },
                enabled = !metalLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (metalLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.saveAndRefresh)
            }
        }
        metalError?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        metalPrices?.let { current ->
            item {
                Text(
                    "${AppStrings.lastPriceUpdate}: ${current.updatedAt}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
        item { Text(AppStrings.iranStocks, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text(AppStrings.stockSearchHint, style = MaterialTheme.typography.bodySmall) }
        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    selectedStock = null
                    selectedQuote = null
                    stockMessage = null
                },
                label = { Text(AppStrings.stockSearch) },
                trailingIcon = {
                    if (searchLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        searchError?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        if (!searchLoading && searchText.trim().length >= 2 && selectedStock == null && searchResults.isEmpty() && searchError == null) {
            item { Text(AppStrings.stockNoResults, style = MaterialTheme.typography.bodySmall) }
        }
        if (searchResults.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        searchResults.take(8).forEachIndexed { index, result ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectStock(result) }
                                    .padding(12.dp)
                            ) {
                                Text(result.symbol, fontWeight = FontWeight.Bold)
                                Text(result.name, style = MaterialTheme.typography.bodySmall)
                                if (result.market.isNotBlank()) {
                                    Text(
                                        "${AppStrings.market}: ${result.market}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            if (index != searchResults.take(8).lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }

        selectedStock?.let { selected ->
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${selected.symbol} • ${selected.name}", fontWeight = FontWeight.Bold)
                        selectedQuote?.let {
                            Text(
                                "${AppStrings.stockCurrentPrice}: ${Money.format2(it.lastPriceToman)} ${AppStrings.moneyUnit}",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = sanitizeNumberInput(it) },
                            label = { Text(AppStrings.stockQuantity) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = buyPriceText,
                            onValueChange = { buyPriceText = sanitizeNumberInput(it) },
                            label = { Text(AppStrings.stockBuyPrice) },
                            visualTransformation = ThousandsSeparatorTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val isExisting = stocks.any { it.instrumentCode == selected.instrumentCode }
                        Button(
                            onClick = {
                                val quantity = quantityText.toDoubleOrNull()
                                val buyPrice = buyPriceText.toDoubleOrNull()
                                if (quantity == null || quantity <= 0.0 || buyPrice == null || buyPrice <= 0.0) {
                                    stockError = AppStrings.stockInvalidInput
                                    return@Button
                                }
                                stockSaving = true
                                stockError = null
                                stockMessage = null
                                scope.launch {
                                    val existing = stocks.firstOrNull { it.instrumentCode == selected.instrumentCode }
                                    val latestQuote = selectedQuote?.takeIf { it.instrumentCode == selected.instrumentCode }
                                        ?: runCatching { TsetmcStockService.quote(selected.instrumentCode) }
                                            .onFailure { CrashLogger.log("assets: TSETMC quote before save failed", it) }
                                            .getOrNull()
                                    runCatching {
                                        viewModel.saveStockAsset(
                                            StockAssetEntity(
                                                instrumentCode = selected.instrumentCode,
                                                symbol = selected.symbol,
                                                name = selected.name,
                                                quantity = quantity,
                                                buyPriceToman = buyPrice,
                                                lastPriceToman = latestQuote?.lastPriceToman ?: existing?.lastPriceToman,
                                                lastPriceUpdatedAt = latestQuote?.fetchedAtMillis ?: existing?.lastPriceUpdatedAt
                                            )
                                        )
                                    }.onSuccess {
                                        stockMessage = AppStrings.stockSaved
                                        selectedStock = null
                                        selectedQuote = null
                                        searchText = ""
                                        quantityText = ""
                                        buyPriceText = ""
                                    }.onFailure {
                                        CrashLogger.log("assets: stock database save failed", it)
                                        stockError = it.message ?: AppStrings.stockInvalidInput
                                    }
                                    stockSaving = false
                                }
                            },
                            enabled = !stockSaving && !stockRefreshing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (stockSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(if (isExisting) AppStrings.updateStock else AppStrings.addStock)
                        }
                    }
                }
            }
        }

        stockMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.primary) }
        }
        stockError?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        if (stocks.isEmpty()) {
            item { Text(AppStrings.noStocks, style = MaterialTheme.typography.bodySmall) }
        } else {
            item {
                AppCard(
                    filled = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(AppStrings.stockPortfolioValue, style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (pricedStocks.isNotEmpty()) {
                                "${Money.format2(stockValue)} ${AppStrings.moneyUnit}"
                            } else {
                                AppStrings.priceUnavailable
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (pricedStocks.isNotEmpty()) {
                            Text(
                                "${AppStrings.stockProfit}: ${Money.format2(stockProfit)} ${AppStrings.moneyUnit}",
                                color = profitColor(stockProfit),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${AppStrings.stockProfitPercent}: ${
                                    Money.input(kotlin.math.round(profitPercent * 100.0) / 100.0)
                                }٪",
                                color = profitColor(stockProfit),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { refreshStocks() },
                    enabled = !stockRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (stockRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.refreshStockPrices)
                }
            }
        }

        if (stocks.isNotEmpty()) {
            item {
                StockAssetsTable(
                    stocks = stocks,
                    deleteEnabled = !stockRefreshing,
                    onEdit = { stock ->
                        selectStock(
                            StockSearchResult(
                                instrumentCode = stock.instrumentCode,
                                symbol = stock.symbol,
                                name = stock.name,
                                market = ""
                            ),
                            existing = stock
                        )
                    },
                    onDelete = viewModel::deleteStockAsset
                )
            }
        }
    }
}

@Composable
private fun AssetEditor(label: String, amount: String, price: Double?, onChange: (String) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = amount,
                onValueChange = { onChange(sanitizeNumberInput(it)) },
                label = { Text(AppStrings.weightGrams) },
                modifier = Modifier.fillMaxWidth()
            )
            price?.let {
                Text(
                    "${AppStrings.pricePerGram}: ${Money.format2(it)} ${AppStrings.moneyUnit}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "${AppStrings.assetValue}: ${Money.format2((amount.toDoubleOrNull() ?: 0.0) * it)} ${AppStrings.moneyUnit}"
                )
            }
        }
    }
}

@Composable
private fun StockAssetsTable(
    stocks: List<StockAssetEntity>,
    onEdit: (StockAssetEntity) -> Unit,
    onDelete: (StockAssetEntity) -> Unit,
    deleteEnabled: Boolean
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp)) {
            StockTableRow(
                values = listOf(
                    AppStrings.stockSymbol,
                    AppStrings.stockQuantity,
                    AppStrings.stockBuyPrice,
                    AppStrings.stockCurrentPrice,
                    AppStrings.assetValue,
                    AppStrings.stockProfit
                ),
                header = true
            )
            HorizontalDivider()
            stocks.forEach { stock ->
                val currentPrice = stock.lastPriceToman
                val currentValue = currentPrice?.let { stock.quantity * it }
                val profit = currentValue?.minus(stock.quantity * stock.buyPriceToman)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    StockTableCell(stock.symbol, 92.dp, FontWeight.Bold)
                    StockTableCell(Money.input(stock.quantity), 84.dp)
                    StockTableCell(Money.format2(stock.buyPriceToman), 132.dp)
                    StockTableCell(currentPrice?.let(Money::format2) ?: "—", 132.dp)
                    StockTableCell(currentValue?.let(Money::format2) ?: "—", 140.dp)
                    StockTableCell(profit?.let(Money::format2) ?: "—", 124.dp, FontWeight.Bold, profit?.let(::profitColor))
                    IconButton(onClick = { onEdit(stock) }) { Icon(Icons.Filled.Edit, contentDescription = AppStrings.edit) }
                    IconButton(onClick = { onDelete(stock) }, enabled = deleteEnabled) {
                        Icon(Icons.Filled.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StockTableRow(values: List<String>, header: Boolean) {
    val widths = listOf(92.dp, 84.dp, 132.dp, 132.dp, 140.dp, 124.dp)
    Row {
        values.forEachIndexed { index, value ->
            StockTableCell(value, widths[index], if (header) FontWeight.Bold else FontWeight.Normal)
        }
        Spacer(Modifier.width(96.dp))
    }
}

@Composable
private fun StockTableCell(
    value: String,
    width: androidx.compose.ui.unit.Dp,
    weight: FontWeight = FontWeight.Normal,
    color: Color? = null
) {
    Text(
        text = value,
        modifier = Modifier.width(width).padding(horizontal = 6.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = weight,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

private fun profitColor(profit: Double): Color =
    if (profit >= 0.0) Color(0xFF1B7A5A) else Color(0xFFE8604C)
