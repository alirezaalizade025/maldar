package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme
import com.personalfinance.tracker.ui.design.components.AccountCard
import com.personalfinance.tracker.ui.design.components.AmountText
import com.personalfinance.tracker.ui.design.components.AppButton
import com.personalfinance.tracker.ui.design.components.AppCard
import com.personalfinance.tracker.ui.design.components.AppCardStyle
import com.personalfinance.tracker.ui.design.components.EmptyState
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.Digits
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import com.personalfinance.tracker.util.JalaliCalendar

@Composable
fun BankAccountsScreen(viewModel: FinanceViewModel, navController: NavController? = null) {
    MaldarDesignTheme {
        BankAccountsContent(viewModel, navController)
    }
}

@Composable
private fun BankAccountsContent(viewModel: FinanceViewModel, navController: NavController?) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val senders by viewModel.smsSenders.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val currentMonthRange = remember { JalaliCalendar.jalaliMonthRange(java.util.Calendar.getInstance(), 0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddAccount by remember { mutableStateOf(false) }
    var showEditAccount by remember { mutableStateOf<com.personalfinance.tracker.data.BankAccountEntity?>(null) }
    var showDeleteAccount by remember { mutableStateOf<com.personalfinance.tracker.data.BankAccountEntity?>(null) }
    var refreshingId by remember { mutableStateOf<Long?>(null) }
    var refreshingAll by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MaldarDesign.spacing.lg,
            end = MaldarDesign.spacing.lg,
            top = MaldarDesign.spacing.lg,
            bottom = MaldarDesign.spacing.section
        ),
        verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.bankAccounts, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = {
                        if (accounts.isEmpty() || refreshingAll) return@IconButton
                        refreshingAll = true
                        scope.launch {
                            var updated = 0
                            accounts.forEach { acc ->
                                val accSenders = senders.filter { it.bankAccountId == acc.id }.map { it.senderId }
                                if (accSenders.isNotEmpty()) {
                                    val res = SmsInboxReader.lastSmsForSenders(context, accSenders, acc.accountLast4)
                                    if (res.amount != null) {
                                        viewModel.updateBankAccount(acc.copy(balance = res.amount))
                                        updated++
                                    }
                                }
                            }
                            refreshingAll = false
                            message = if (updated > 0) AppStrings.refreshDone else AppStrings.refreshFailed
                        }
                    },
                    enabled = accounts.isNotEmpty() && !refreshingAll,
                    modifier = Modifier.semantics {
                        contentDescription = AppStrings.refreshAll
                        if (refreshingAll) stateDescription = AppStrings.refreshingAccount
                    }
                ) {
                    if (refreshingAll) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = AppStrings.refreshAll)
                }
            }
        }

        // Show total remainder of all accounts at the top
        if (accounts.isNotEmpty()) {
            item {
                val totalBalance = accounts.sumOf { it.balance }
                val totalLoanRemainder = loans.filter { !it.isPaid }.sumOf { it.remainingAmount }
                AppCard(style = AppCardStyle.HERO, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)) {
                        Text(AppStrings.total, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        AmountText(Money.format2(totalBalance), style = MaterialTheme.typography.headlineLarge)
                        if (totalLoanRemainder > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(AppStrings.loanRemainderTotal, style = MaterialTheme.typography.labelSmall)
                                Text(Money.format2(totalLoanRemainder) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                EmptyState(
                    title = AppStrings.bankAccounts,
                    message = AppStrings.noAccounts,
                    actionLabel = AppStrings.addAccount,
                    onAction = { showAddAccount = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaldarDesign.spacing.section)
                )
            }
        }

        items(accounts) { acc ->
            val accSenders = senders.filter { it.bankAccountId == acc.id }
            val attachedLoans = loans.filter { it.bankAccountId == acc.id }
            val activeAttachedLoans = attachedLoans.filter { !it.isPaid }
            val paidThisMonth = transactions.filter {
                it.bankAccountId == acc.id && it.loanId != null &&
                    it.dateMillis in currentMonthRange.first..currentMonthRange.second
            }.sumOf { it.amount }
            val installmentDue = activeAttachedLoans.sumOf {
                (if (it.installment > 0.0) it.installment else it.remainingAmount)
                    .coerceAtMost(it.remainingAmount)
            }
            val remainingThisMonth = (installmentDue - paidThisMonth).coerceAtLeast(0.0)
            val refreshAccount: () -> Unit = {
                if (accSenders.isEmpty()) {
                    message = AppStrings.refreshFailed
                } else {
                    refreshingId = acc.id
                    scope.launch {
                        val res = SmsInboxReader.lastSmsForSenders(context, accSenders.map { it.senderId }, acc.accountLast4)
                        if (res.amount != null) {
                            viewModel.updateBankAccount(acc.copy(balance = res.amount))
                            message = AppStrings.refreshDone
                        } else {
                            message = AppStrings.refreshFailed
                        }
                        refreshingId = null
                    }
                }
            }
            AccountCard(
                title = acc.accountLabel,
                bankName = acc.bankName,
                balance = Money.format2(acc.balance),
                maskedIdentifier = acc.accountLast4.takeIf { it.isNotBlank() }?.let { "${AppStrings.cardEnding}: •••• $it" },
                status = when {
                    refreshingId == acc.id -> AppStrings.refreshingAccount
                    accSenders.isEmpty() -> AppStrings.noSendersForAccount
                    else -> null
                },
                loanSummary = if (attachedLoans.isEmpty()) emptyList() else listOf(
                    "${AppStrings.attachedLoansTotal}: ${Money.format2(attachedLoans.sumOf { it.principal })} ${AppStrings.moneyUnit}",
                    "${AppStrings.loansSummaryRemain}: ${Money.format2(activeAttachedLoans.sumOf { it.remainingAmount })} ${AppStrings.moneyUnit}"
                ),
                monthlyInstallmentRemainder = remainingThisMonth.takeIf { it > 0.0 }?.let {
                    "${AppStrings.installmentRemainingThisMonth}: ${Money.format2(it)} ${AppStrings.moneyUnit}"
                },
                refreshing = refreshingId == acc.id,
                onClick = navController?.let { controller -> { controller.navigate("account_sms/${acc.id}") } },
                onEdit = { showEditAccount = acc },
                onRefresh = refreshAccount,
                onSms = navController?.let { controller -> { controller.navigate("account_sms/${acc.id}") } },
                onDelete = { showDeleteAccount = acc },
                editDescription = AppStrings.edit,
                refreshDescription = AppStrings.refresh,
                smsDescription = AppStrings.showSms,
                deleteDescription = AppStrings.delete,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (accounts.isNotEmpty()) {
            item {
                AppButton(
                    text = AppStrings.addAccount,
                    onClick = { showAddAccount = true },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                )
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter).semantics { liveRegion = LiveRegionMode.Polite }
    )
    }

    if (showAddAccount) {
        AddAccountDialog(onDismiss = { showAddAccount = false }, onAdd = { bank, label, last4, bal, senderIds ->
            viewModel.addBankAccount(bank, label, bal, last4) { accountId ->
                senderIds.filter { it.isNotBlank() }.forEach { viewModel.addSmsSender(it.trim(), accountId, "") }
            }
            showAddAccount = false
        })
    }

    showEditAccount?.let { acc ->
        EditAccountDialog(account = acc, allSenders = senders, context = context, viewModel = viewModel, onDismiss = { showEditAccount = null }, onSave = { bank, label, last4, bal ->
            viewModel.updateBankAccount(acc.copy(bankName = bank, accountLabel = label, accountLast4 = last4, balance = bal))
            showEditAccount = null
        })
    }

    showDeleteAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { showDeleteAccount = null },
            title = { Text(AppStrings.deleteAccountConfirmTitle) },
            text = { Text(AppStrings.deleteAccountConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBankAccount(acc)
                        showDeleteAccount = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(AppStrings.delete) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccount = null }) { Text(AppStrings.cancel) } }
        )
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Double, List<String>) -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var bankNameError by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var accountLast4 by remember { mutableStateOf("") }
    var last4Error by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val detectedSenders = remember {
        SmsInboxReader.recentSenders(context)
    }
    var addedSenders by remember { mutableStateOf<List<SmsInboxReader.DetectedSender>>(emptyList()) }
    var senderQuery by remember { mutableStateOf("") }
    var senderMenuExpanded by remember { mutableStateOf(false) }

    val filteredSenders = remember(senderQuery, detectedSenders, addedSenders) {
        detectedSenders.filter { ds ->
            (ds.displayName != null && ds.displayName.contains(senderQuery, ignoreCase = true) ||
                ds.address.contains(senderQuery, ignoreCase = true)) &&
                !addedSenders.any { a -> a.address.equals(ds.address, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.addAccount) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it; bankNameError = false },
                    label = { Text(AppStrings.bankName) },
                    isError = bankNameError,
                    supportingText = if (bankNameError) { { Text(AppStrings.requiredField) } } else null
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(AppStrings.label + " (" + AppStrings.optional + ")") })
                OutlinedTextField(
                    value = accountLast4,
                    onValueChange = {
                        accountLast4 = Digits.toEnglish(it).filter(Char::isDigit).take(4)
                        last4Error = false
                    },
                    label = { Text(AppStrings.last4 + " (" + AppStrings.optional + ")") },
                    isError = last4Error,
                    supportingText = {
                        Text(if (last4Error) AppStrings.last4Invalid else AppStrings.last4Hint)
                    },
                    singleLine = true
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.openingBalance) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                HorizontalDivider()
                Text(AppStrings.smsSenders, style = MaterialTheme.typography.titleMedium)
                Text(AppStrings.smsSendersHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                addedSenders.forEach { ds ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ds.displayName ?: ds.address, style = MaterialTheme.typography.bodyMedium)
                            if (ds.displayName != null) {
                                Text(ds.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        IconButton(onClick = { addedSenders = addedSenders - ds }) {
                            Icon(Icons.Filled.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = senderMenuExpanded, onExpandedChange = { senderMenuExpanded = it }) {
                    OutlinedTextField(
                        value = senderQuery,
                        onValueChange = { senderQuery = it; senderMenuExpanded = true },
                        label = { Text(AppStrings.detectedSenders) },
                        placeholder = { Text(AppStrings.senderHint) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = senderMenuExpanded, onDismissRequest = { senderMenuExpanded = false }) {
                        if (filteredSenders.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(AppStrings.noDetectedSenders) },
                                onClick = { senderMenuExpanded = false }
                            )
                        }
                        filteredSenders.forEach { ds ->
                            DropdownMenuItem(text = {
                                Column {
                                    Text(ds.displayName ?: ds.address)
                                    if (ds.displayName != null) {
                                        Text(ds.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }, onClick = {
                                addedSenders = addedSenders + ds
                                senderQuery = ""
                                senderMenuExpanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = senderQuery,
                    onValueChange = { senderQuery = it },
                    label = { Text(AppStrings.senderId + " (" + AppStrings.optional + ")") },
                    placeholder = { Text(AppStrings.senderHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val trimmed = senderQuery.trim()
                    if (trimmed.isNotBlank() && !addedSenders.any { it.address.equals(trimmed, ignoreCase = true) }) {
                        addedSenders = addedSenders + SmsInboxReader.DetectedSender(trimmed, null)
                        senderQuery = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.addSender) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (bankName.isBlank()) {
                    bankNameError = true
                } else if (accountLast4.isNotEmpty() && accountLast4.length != 4) {
                    last4Error = true
                } else {
                    val finalLabel = label.ifBlank { bankName }
                    onAdd(bankName, finalLabel, accountLast4, balance.toDoubleOrNull() ?: 0.0, addedSenders.map { it.address })
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
    var balance by remember { mutableStateOf(Money.input(account.balance)) }
    var accountLast4 by remember { mutableStateOf(account.accountLast4) }
    var last4Error by remember { mutableStateOf(false) }

    val accountSenders = allSenders.filter { it.bankAccountId == account.id }
    var senderQuery by remember { mutableStateOf("") }
    var senderMenuExpanded by remember { mutableStateOf(false) }
    val detectedSenders = remember {
        SmsInboxReader.recentSenders(context)
    }
    val filteredSenders = remember(senderQuery, detectedSenders, accountSenders) {
        detectedSenders.filter { ds ->
            (ds.displayName != null && ds.displayName.contains(senderQuery, ignoreCase = true) ||
                ds.address.contains(senderQuery, ignoreCase = true)) &&
                !accountSenders.any { added -> added.senderId.equals(ds.address, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.edit) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it; bankNameError = false },
                    label = { Text(AppStrings.bankName) },
                    isError = bankNameError,
                    supportingText = if (bankNameError) { { Text(AppStrings.requiredField) } } else null
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(AppStrings.label + " (" + AppStrings.optional + ")") })
                OutlinedTextField(
                    value = accountLast4,
                    onValueChange = {
                        accountLast4 = Digits.toEnglish(it).filter(Char::isDigit).take(4)
                        last4Error = false
                    },
                    label = { Text(AppStrings.last4 + " (" + AppStrings.optional + ")") },
                    isError = last4Error,
                    supportingText = {
                        Text(if (last4Error) AppStrings.last4Invalid else AppStrings.last4Hint)
                    },
                    singleLine = true
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = sanitizeNumberInput(it) },
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

                ExposedDropdownMenuBox(expanded = senderMenuExpanded, onExpandedChange = { senderMenuExpanded = it }) {
                    OutlinedTextField(
                        value = senderQuery,
                        onValueChange = { senderQuery = it; senderMenuExpanded = true },
                        label = { Text(AppStrings.detectedSenders) },
                        placeholder = { Text(AppStrings.senderHint) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = senderMenuExpanded, onDismissRequest = { senderMenuExpanded = false }) {
                        if (filteredSenders.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(AppStrings.noDetectedSenders) },
                                onClick = { senderMenuExpanded = false }
                            )
                        }
                        filteredSenders.forEach { ds ->
                            DropdownMenuItem(text = {
                                Column {
                                    Text(ds.displayName ?: ds.address)
                                    if (ds.displayName != null) {
                                        Text(ds.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }, onClick = {
                                viewModel.addSmsSender(ds.address.trim(), account.id, "")
                                senderQuery = ""
                                senderMenuExpanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = senderQuery,
                    onValueChange = { senderQuery = it },
                    label = { Text(AppStrings.senderId + " (" + AppStrings.optional + ")") },
                    placeholder = { Text(AppStrings.senderHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val trimmed = senderQuery.trim()
                    if (trimmed.isNotBlank()) {
                        viewModel.addSmsSender(trimmed, account.id, "")
                        senderQuery = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(AppStrings.addSender) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (bankName.isBlank()) {
                    bankNameError = true
                } else if (accountLast4.isNotEmpty() && accountLast4.length != 4) {
                    last4Error = true
                } else {
                    val finalLabel = label.ifBlank { bankName }
                    onSave(bankName, finalLabel, accountLast4, balance.toDoubleOrNull() ?: 0.0)
                }
            }) { Text(AppStrings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
