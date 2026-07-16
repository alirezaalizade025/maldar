package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
fun BankAccountsScreen(viewModel: FinanceViewModel) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val senders by viewModel.smsSenders.collectAsState()

    var showAddAccount by remember { mutableStateOf(false) }
    var showAddSender by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.bankAccounts, style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { showAddAccount = true }) { Icon(Icons.Filled.Add, contentDescription = AppStrings.addAccount) }
            }
        }

        if (accounts.isEmpty()) {
            item { Text(AppStrings.noAccounts, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(accounts) { acc ->
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(acc.accountLabel, fontWeight = FontWeight.Medium)
                        Text("${acc.bankName} •••• ${acc.accountLast4}", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Money.format2(acc.balance) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { viewModel.deleteBankAccount(acc) }) {
                            Icon(Icons.Filled.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)); Divider() }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(AppStrings.smsSenders, style = MaterialTheme.typography.titleLarge)
                    Text(AppStrings.smsSendersHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                IconButton(onClick = { showAddSender = true }, enabled = accounts.isNotEmpty()) {
                    Icon(Icons.Filled.Add, contentDescription = AppStrings.addSender)
                }
            }
        }

        if (accounts.isEmpty()) {
            item { Text(AppStrings.addAccountFirst, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(senders) { s ->
            val accName = accounts.firstOrNull { it.id == s.bankAccountId }?.accountLabel ?: "---"
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(s.senderId, fontWeight = FontWeight.Medium)
                        Text("→ $accName", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { viewModel.deleteSmsSender(s) }) {
                        Icon(Icons.Filled.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showAddAccount) {
        AddAccountDialog(onDismiss = { showAddAccount = false }, onAdd = { bank, label, last4, bal ->
            viewModel.addBankAccount(bank, label, last4, bal)
            showAddAccount = false
        })
    }

    if (showAddSender) {
        AddSenderDialog(
            accounts = accounts,
            onDismiss = { showAddSender = false },
            onAdd = { senderId, accId, label ->
                viewModel.addSmsSender(senderId, accId, label)
                showAddSender = false
            }
        )
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Double) -> Unit) {
    var bankName by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.addAccount) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text(AppStrings.bankName) })
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(AppStrings.label) })
                OutlinedTextField(value = last4, onValueChange = { last4 = it.take(4) }, label = { Text(AppStrings.last4) })
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(AppStrings.openingBalance) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (bankName.isNotBlank() && label.isNotBlank()) {
                    onAdd(bankName, label, last4, balance.toDoubleOrNull() ?: 0.0)
                }
            }) { Text(AppStrings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}

@Composable
private fun AddSenderDialog(
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, Long, String) -> Unit
) {
    var senderId by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.addSender) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    AppStrings.senderHint,
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(value = senderId, onValueChange = { senderId = it }, label = { Text(AppStrings.senderId) })
                ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == selectedAccountId }?.accountLabel ?: "",
                        onValueChange = {}, readOnly = true,
                        label = { Text(AppStrings.linkToAccount) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.accountLabel) }, onClick = { selectedAccountId = acc.id; menuExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (senderId.isNotBlank()) onAdd(senderId.trim(), selectedAccountId, "")
            }) { Text(AppStrings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
