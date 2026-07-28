package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.AssetType
import com.personalfinance.tracker.ui.theme.AppCard
import com.personalfinance.tracker.util.*
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

@Composable
fun FinancialAssetsScreen(viewModel: FinanceViewModel) {
    val assets by viewModel.financialAssets.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("metal_prices", 0) }
    var apiKey by remember { mutableStateOf(prefs.getString("brs_api_key", "") ?: "") }
    var goldAmount by remember(assets) {
        mutableStateOf(assets.firstOrNull { it.type == AssetType.GOLD_18K }?.quantityGrams?.let(Money::input) ?: "")
    }
    var silverAmount by remember(assets) {
        mutableStateOf(assets.firstOrNull { it.type == AssetType.SILVER_999 }?.quantityGrams?.let(Money::input) ?: "")
    }
    var prices by remember { mutableStateOf<MetalPrices?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        prefs.edit().putString("brs_api_key", apiKey.trim()).apply()
        loading = true
        error = null
        scope.launch {
            runCatching { MetalPriceService.fetch(apiKey.trim()) }
                .onSuccess { prices = it }
                .onFailure { error = it.message ?: AppStrings.assetPriceFailed }
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(AppStrings.financialAssets, style = MaterialTheme.typography.headlineMedium) }
        item {
            Text(AppStrings.assetsHint, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(AppStrings.brsApiKey) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            AssetEditor(AppStrings.gold18, goldAmount, prices?.gold18TomanPerGram) { goldAmount = it }
        }
        item {
            AssetEditor(AppStrings.silver999, silverAmount, prices?.silver999TomanPerGram) { silverAmount = it }
        }
        item {
            Button(
                onClick = {
                    viewModel.saveFinancialAsset(AssetType.GOLD_18K, goldAmount.toDoubleOrNull() ?: 0.0)
                    viewModel.saveFinancialAsset(AssetType.SILVER_999, silverAmount.toDoubleOrNull() ?: 0.0)
                    refresh()
                },
                enabled = !loading && apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.saveAndRefresh)
            }
        }
        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        prices?.let { current ->
            item {
                val total = (goldAmount.toDoubleOrNull() ?: 0.0) * current.gold18TomanPerGram +
                    (silverAmount.toDoubleOrNull() ?: 0.0) * current.silver999TomanPerGram
                AppCard(filled = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(AppStrings.assetsTotal, style = MaterialTheme.typography.labelSmall)
                        Text("${Money.format2(total)} ${AppStrings.moneyUnit}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${AppStrings.lastPriceUpdate}: ${current.updatedAt}", style = MaterialTheme.typography.labelSmall)
                    }
                }
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
                Text("${AppStrings.pricePerGram}: ${Money.format2(it)} ${AppStrings.moneyUnit}", style = MaterialTheme.typography.labelSmall)
                Text("${AppStrings.assetValue}: ${Money.format2((amount.toDoubleOrNull() ?: 0.0) * it)} ${AppStrings.moneyUnit}")
            }
        }
    }
}
