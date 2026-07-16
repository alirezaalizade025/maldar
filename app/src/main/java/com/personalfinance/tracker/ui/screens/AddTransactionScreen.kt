package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
fun AddTransactionScreen(viewModel: FinanceViewModel) {
    val accounts by viewModel.bankAccounts.collectAsState()

    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }
    // false = Toman (stored unit), true = Rial (entered value / 10 to convert)
    var rialMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(AppStrings.addTransaction, style = MaterialTheme.typography.headlineMedium)

        SingleChoiceSegmented(
            options = listOf(AppStrings.expense, AppStrings.income),
            selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
            onSelected = {
                type = if (it == 0) TxType.EXPENSE else TxType.INCOME
                category = "" // let CategoryPicker re-default to the first category of the new type
            }
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(AppStrings.amountLabel) },
            visualTransformation = ThousandsSeparatorTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(AppStrings.unit, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = !rialMode,
                onClick = { rialMode = false },
                label = { Text(AppStrings.toman) }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = rialMode,
                onClick = { rialMode = true },
                label = { Text(AppStrings.rial) }
            )
        }

        CategoryPicker(
            viewModel = viewModel,
            type = type,
            selected = category,
            onSelected = { category = it }
        )

        ExposedDropdownMenuBox(expanded = accountMenuExpanded, onExpandedChange = { accountMenuExpanded = it }) {
            OutlinedTextField(
                value = accounts.firstOrNull { it.id == selectedAccountId }?.accountLabel ?: AppStrings.noneCash,
                onValueChange = {}, readOnly = true,
                label = { Text(AppStrings.bankAccount) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(AppStrings.noneCash) }, onClick = { selectedAccountId = null; accountMenuExpanded = false })
                accounts.forEach { acc ->
                    DropdownMenuItem(text = { Text(acc.accountLabel) }, onClick = { selectedAccountId = acc.id; accountMenuExpanded = false })
                }
            }
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text(AppStrings.noteOptional) },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val stored = if (rialMode) amount / 10.0 else amount
                    viewModel.addTransaction(stored, type, category, note, selectedAccountId)
                    confirmationMessage = AppStrings.saved
                    amountText = ""; note = ""
                } else {
                    confirmationMessage = AppStrings.invalidAmount
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(AppStrings.save) }

        confirmationMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SingleChoiceSegmented(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelected(index) },
                label = { Text(label) },
                modifier = Modifier.weight(1f).padding(end = if (index != options.lastIndex) 8.dp else 0.dp)
            )
        }
    }
}
