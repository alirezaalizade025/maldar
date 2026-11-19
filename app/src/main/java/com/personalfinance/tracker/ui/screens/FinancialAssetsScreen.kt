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
    val state: com.personalfinance.tracker.viewmodel.AssetsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = viewModel.assetViewModelFactory
    )
    var stockToDelete by remember { mutableStateOf<StockAssetEntity?>(null) }
    LaunchedEffect(Unit) { state.refreshMetals() }
    LaunchedEffect(stocks.map { it.instrumentCode }) { state.refreshStocks(stocks) }
    LaunchedEffect(state.searchText, state.selectedStock?.instrumentCode) { state.searchStocks(state.searchText) }

    val goldValue = (goldAmount.toDoubleOrNull() ?: 0.0) * (state.metalPrices?.gold18TomanPerGram ?: 0.0)
    val silverValue = (silverAmount.toDoubleOrNull() ?: 0.0) * (state.metalPrices?.silver999TomanPerGram ?: 0.0)
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

        if (state.metalPrices != null || stocks.any { it.lastPriceToman != null }) {
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
            AssetEditor(AppStrings.gold18, goldAmount, state.metalPrices?.gold18TomanPerGram) { goldAmount = it }
        }
        item {
            AssetEditor(AppStrings.silver999, silverAmount, state.metalPrices?.silver999TomanPerGram) { silverAmount = it }
        }
        item {
            Button(
                onClick = {
                    state.refreshMetals(goldAmount.toDoubleOrNull() ?: 0.0, silverAmount.toDoubleOrNull() ?: 0.0)
                },
                enabled = !state.metalLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.metalLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.saveAndRefresh)
            }
        }
        state.metalError?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        state.metalPrices?.let { current ->
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
                enabled = !state.stockSaving,
                value = state.searchText,
                onValueChange = {
                    state.searchText = it
                    state.selectedStock = null
                    state.selectedQuote = null
                    state.stockMessage = null
                },
                label = { Text(AppStrings.stockSearch) },
                trailingIcon = {
                    if (state.searchLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        state.searchError?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        if (!state.searchLoading && state.searchText.trim().length >= 2 && state.selectedStock == null && state.searchResults.isEmpty() && state.searchError == null) {
            item { Text(AppStrings.stockNoResults, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.searchResults.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.searchResults.take(8).forEachIndexed { index, result ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.stockSaving) { state.selectStock(result) }
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
                            if (index != state.searchResults.take(8).lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }

        state.selectedStock?.let { selected ->
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${selected.symbol} • ${selected.name}", fontWeight = FontWeight.Bold)
                        state.selectedQuote?.let {
                            Text(
                                "${AppStrings.stockCurrentPrice}: ${Money.format2(it.lastPriceToman)} ${AppStrings.moneyUnit}",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            enabled = !state.stockSaving,
                            value = state.quantityText,
                            onValueChange = { state.quantityText = sanitizeNumberInput(it) },
                            label = { Text(AppStrings.stockQuantity) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            enabled = !state.stockSaving,
                            value = state.buyPriceText,
                            onValueChange = { state.buyPriceText = sanitizeNumberInput(it) },
                            label = { Text(AppStrings.stockBuyPrice) },
                            visualTransformation = ThousandsSeparatorTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val isExisting = stocks.any { it.instrumentCode == selected.instrumentCode }
                        Button(
                            onClick = { state.saveStock(stocks) },
                            enabled = !state.stockSaving && !state.stockRefreshing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.stockSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(if (isExisting) AppStrings.updateStock else AppStrings.addStock)
                        }
                    }
                }
            }
        }

        state.stockMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.primary) }
        }
        state.stockError?.let { message ->
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
                    onClick = { state.refreshStocks(stocks) },
                    enabled = !state.stockRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.stockRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
                    deleteEnabled = !state.stockRefreshing && !state.stockSaving,
                    onEdit = { stock ->
                        state.selectStock(
                            StockSearchResult(
                                instrumentCode = stock.instrumentCode,
                                symbol = stock.symbol,
                                name = stock.name,
                                market = ""
                            ),
                            existing = stock
                        )
                    },
                    onDelete = { stockToDelete = it }
                )
            }
        }

    }

    if (stockToDelete != null) {
        val stock = stockToDelete!!
        AlertDialog(
            onDismissRequest = { if (!state.stockSaving) stockToDelete = null },
            title = { Text(AppStrings.deleteStockConfirmTitle) },
            text = { Text(AppStrings.deleteStockConfirmBody.fa(stock.symbol)) },
            confirmButton = {
                TextButton(
                    enabled = !state.stockSaving,
                    onClick = {
                        state.deleteStock(stock) { stockToDelete = null }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(AppStrings.delete) }
            },
            dismissButton = { TextButton(onClick = { stockToDelete = null }) { Text(AppStrings.cancel) } }
        )
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
                    IconButton(onClick = { onEdit(stock) }, enabled = deleteEnabled) { Icon(Icons.Filled.Edit, contentDescription = AppStrings.edit) }
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
