package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

@Composable
fun BankAccountsScreen(viewModel: FinanceViewModel) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val senders by viewModel.smsSenders.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddAccount by remember { mutableStateOf(false) }
    var showEditAccount by remember { mutableStateOf<com.personalfinance.tracker.data.BankAccountEntity?>(null) }
    var refreshingId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it) }
    }

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
                        IconButton(
                            onClick = {
                                val accSenders = senders.filter { it.bankAccountId == acc.id }.map { it.senderId }
                                if (accSenders.isEmpty()) { message = AppStrings.refreshFailed; return@IconButton }
                                refreshingId = acc.id
                                scope.launch {
                                    val res = SmsInboxReader.lastSmsForSenders(context, accSenders)
                                    if (res.amount != null) {
                                        viewModel.updateBankAccount(acc.copy(balance = res.amount))
                                        message = AppStrings.refreshDone
                                    } else {
                                        message = AppStrings.refreshFailed
                                    }
                                    refreshingId = null
                                }
                            },
                            enabled = refreshingId != acc.id
                        ) {
                            if (refreshingId == acc.id) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = AppStrings.refresh)
                            }
                        }
                        IconButton(onClick = { showEditAccount = acc }) {
                            Icon(Icons.Filled.Edit, contentDescription = AppStrings.edit)
                        }
                        IconButton(onClick = { viewModel.deleteBankAccount(acc) }) {
                            Icon(Icons.Filled.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    androidx.compose.material3.SnackbarHost(hostState = snackbarHostState)

    if (showAddAccount) {
        AddAccountDialog(onDismiss = { showAddAccount = false }, onAdd = { bank, label, last4, bal ->
            viewModel.addBankAccount(bank, label, last4, bal)
            showAddAccount = false
        })
    }

    showEditAccount?.let { acc ->
        EditAccountDialog(account = acc, allSenders = senders, context = context, viewModel = viewModel, onDismiss = { showEditAccount = null }, onSave = { bank, label, last4, bal ->
            viewModel.updateBankAccount(acc.copy(bankName = bank, accountLabel = label, accountLast4 = last4, balance = bal))
            showEditAccount = null
        })
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Double) -> Unit) {
    var bankName by remember { mutableStateOf("") }
    var bankNameError by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.addAccount) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it; bankNameError = false },
                    label = { Text(AppStrings.bankName) },
                    isError = bankNameError,
                    supportingText = if (bankNameError) { { Text(AppStrings.requiredField) } } else null
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(AppStrings.label + " (" + AppStrings.optional + ")") })
                OutlinedTextField(value = last4, onValueChange = { last4 = it.take(4) }, label = { Text(AppStrings.last4 + " (" + AppStrings.optional + ")") })
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(AppStrings.openingBalance) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (bankName.isBlank()) {
                    bankNameError = true
                } else {
                    val finalLabel = label.ifBlank { bankName }
                    onAdd(bankName, finalLabel, last4, balance.toDoubleOrNull() ?: 0.0)
                }
            }) { Text(AppStrings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}

@Composable
private fun EditAccountDialog(
    account: com.personalfinance.tracker.data.BankAccountEntity,
    allSenders: List<com.personalfinance.tracker.data.SmsSenderEntity>,
    context: android.content.Context,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double) -> Unit
) {
    var bankName by remember { mutableStateOf(account.bankName) }
    var bankNameError by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf(account.accountLabel) }
    var last4 by remember { mutableStateOf(account.accountLast4) }
    var balance by remember { mutableStateOf(account.balance.toString()) }

    val accountSenders = allSenders.filter { it.bankAccountId == account.id }
    var senderId by remember { mutableStateOf("") }
    var senderMenuExpanded by remember { mutableStateOf(false) }
    var showSenderInput by remember { mutableStateOf(false) }
    val detectedSenders = remember {
        SmsInboxReader.recentSenders(context, exclude = allSenders.map { it.senderId }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.edit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it; bankNameError = false },
                    label = { Text(AppStrings.bankName) },
                    isError = bankNameError,
                    supportingText = if (bankNameError) { { Text(AppStrings.requiredField) } } else null
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(AppStrings.label + " (" + AppStrings.optional + ")") })
                OutlinedTextField(value = last4, onValueChange = { last4 = it.take(4) }, label = { Text(AppStrings.last4 + " (" + AppStrings.optional + ")") })
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(AppStrings.openingBalance) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )

                HorizontalDivider()
                Text(AppStrings.smsSenders, style = MaterialTheme.typography.titleMedium)
                Text(AppStrings.smsSendersHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                accountSenders.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(s.senderId, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { viewModel.deleteSmsSender(s) }) {
                            Icon(Icons.Filled.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (showSenderInput) {
                    if (detectedSenders.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = senderMenuExpanded, onExpandedChange = { senderMenuExpanded = it }) {
                            OutlinedTextField(
                                value = senderId,
                                onValueChange = { senderId = it },
                                label = { Text(AppStrings.detectedSenders) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = senderMenuExpanded, onDismissRequest = { senderMenuExpanded = false }) {
                                detectedSenders.forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = { senderId = s; senderMenuExpanded = false })
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = senderId, onValueChange = { senderId = it }, label = { Text(AppStrings.senderId) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (senderId.isNotBlank()) {
                            viewModel.addSmsSender(senderId.trim(), account.id, "")
                            senderId = ""; showSenderInput = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.addSender) }
                } else {
                    OutlinedButton(onClick = { showSenderInput = true }, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.addSender) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (bankName.isBlank()) {
                    bankNameError = true
                } else {
                    val finalLabel = label.ifBlank { bankName }
                    onSave(bankName, finalLabel, last4, balance.toDoubleOrNull() ?: 0.0)
                }
            }) { Text(AppStrings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
