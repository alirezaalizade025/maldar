package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.ui.theme.AppCard
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.util.Date

@Composable
fun DashboardScreen(viewModel: FinanceViewModel, onGoToConfirm: () -> Unit, onGoToReports: () -> Unit = {}) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val pending by viewModel.pendingSms.collectAsState()
    val reviewed by viewModel.reviewedSms.collectAsState()

    var monthIncome by remember { mutableStateOf(0.0) }
    var monthExpense by remember { mutableStateOf(0.0) }
    var monthLoanPaid by remember { mutableStateOf(0.0) }
    var editingTx by remember { mutableStateOf<com.personalfinance.tracker.data.TransactionEntity?>(null) }
    var editingTxForSms by remember { mutableStateOf<com.personalfinance.tracker.data.PendingSmsEntity?>(null) }
    var totalBalance by remember { mutableStateOf(0.0) }
    LaunchedEffect(transactions, accounts) {
        val (inc, exp) = viewModel.monthlyIncomeExpense(0)
        monthIncome = inc; monthExpense = exp
        monthLoanPaid = viewModel.loanPaymentsThisMonth()
        totalBalance = viewModel.totalAccountBalance()
    }

    val allIncome = transactions.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val allExpense = transactions.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(AppStrings.overview, style = MaterialTheme.typography.headlineMedium)
        }

        // First-launch onboarding: guide the user when the app is completely empty.
        if (accounts.isEmpty() && transactions.isEmpty()) {
            item {
                AppCard(filled = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(AppStrings.onboardingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(AppStrings.onboardingBody, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (pending.isNotEmpty()) {
            item { Text(AppStrings.unreadSms, style = MaterialTheme.typography.titleLarge) }
            items(pending) { p ->
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { editingTxForSms = p },
                            role = androidx.compose.ui.semantics.Role.Button
                        )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${p.parsedType?.name ?: AppStrings.unknown} • ${p.parsedAmount?.let { Money.format2(it) + " " + AppStrings.moneyUnit } ?: AppStrings.amountUnclear}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(AppStrings.from + " ${p.sender}", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { viewModel.rejectPendingSms(p) }) { Text(AppStrings.ignore) }
                    }
                }
            }
        }

        if (reviewed.isNotEmpty()) {
            item {
                var expanded by remember { mutableStateOf(false) }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(AppStrings.reviewedSms.format(reviewed.size), style = MaterialTheme.typography.titleMedium)
                            Text(if (expanded) AppStrings.collapse else AppStrings.expand, style = MaterialTheme.typography.labelSmall)
                        }
                        if (expanded) {
                            reviewed.take(30).forEach { p ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.sender, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                                        Text(p.rawMessage, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { viewModel.deletePendingSms(p) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = AppStrings.delete, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard(filled = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(AppStrings.totalBalance, color = Color.White.copy(alpha = 0.85f))
                    val animatedBalance by animateFloatAsState(
                        targetValue = totalBalance.toFloat(),
                        animationSpec = tween(durationMillis = 400),
                        label = "balance"
                    )
                    Text(
                        Money.format2(animatedBalance.toDouble()) + " " + AppStrings.moneyUnit,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(AppStrings.monthIncome, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Text(Money.format2(monthIncome) + " " + AppStrings.moneyUnit, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text(AppStrings.monthExpense, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Text(Money.format2(monthExpense + monthLoanPaid) + " " + AppStrings.moneyUnit, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (monthLoanPaid > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(AppStrings.loanPaidThisMonth, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Text(Money.format2(monthLoanPaid) + " " + AppStrings.moneyUnit, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item { Text(AppStrings.recentTransactions, style = MaterialTheme.typography.titleLarge) }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(AppStrings.transactionCount.format(transactions.size), style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(allIncome) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFF1B7A5A))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(AppStrings.allTransactionsSum, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(allExpense) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFFE8604C))
                    }
                }
            }
        }

        if (transactions.isEmpty()) {
            item { Text(AppStrings.noTransactions, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(transactions.take(15)) { tx ->
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { editingTx = tx },
                        role = androidx.compose.ui.semantics.Role.Button
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(tx.category, fontWeight = FontWeight.Medium)
                        Text(JalaliCalendar.formatDateTime(tx.dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        if (tx.note.isNotBlank()) {
                            Text(tx.note, style = MaterialTheme.typography.labelSmall)
                        }
                        tx.balanceAfter?.let {
                            Text(
                                "${AppStrings.remainedAfter}: ${Money.format2(it)} ${AppStrings.moneyUnit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        (if (tx.type == TxType.INCOME) "+ " else "- ") + Money.format2(tx.amount) + " " + AppStrings.moneyUnit,
                        color = if (tx.type == TxType.INCOME) Color(0xFF1B7A5A) else Color(0xFFE8604C),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    editingTx?.let { tx ->
        EditTransactionDialog(
            tx = tx,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { editingTx = null }
        )
    }

    editingTxForSms?.let { p ->
        SmsConfirmDialog(pending = p, accounts = accounts, viewModel = viewModel, onDismiss = { editingTxForSms = null })
    }
}

@Composable
private fun EditTransactionDialog(
    tx: com.personalfinance.tracker.data.TransactionEntity,
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(tx.amount.toString()) }
    var type by remember { mutableStateOf(tx.type) }
    var category by remember { mutableStateOf(tx.category) }
    var note by remember { mutableStateOf(tx.note) }
    var selectedAccountId by remember { mutableStateOf(tx.bankAccountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(AppStrings.deleteTransactionConfirmTitle) },
            text = { Text(AppStrings.deleteTransactionConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTransaction(tx); onDismiss() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(AppStrings.delete) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(AppStrings.cancel) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.editTransaction) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmented(
                    options = listOf(AppStrings.expense, AppStrings.income),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelected = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME; category = "" }
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.amountLabel) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                CategoryPicker(viewModel = viewModel, type = type, selected = category, onSelected = { category = it })
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
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(AppStrings.noteOptional) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0 && category.isNotBlank()) {
                    viewModel.updateTransaction(
                        tx.copy(amount = amount, type = type, category = category, note = note, bankAccountId = selectedAccountId)
                    )
                    onDismiss()
                }
            }) { Text(AppStrings.save) }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(AppStrings.delete) }
        }
    )
}

@Composable
private fun SmsConfirmDialog(
    pending: com.personalfinance.tracker.data.PendingSmsEntity,
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(pending.parsedAmount?.toString() ?: "") }
    var type by remember { mutableStateOf(pending.parsedType ?: TxType.EXPENSE) }
    // Default to a generic category so Save never silently no-ops when the
    // categories table is empty (e.g. after a DB reset left it un-seeded).
    val fallbackCategory = "سایر"
    var category by remember { mutableStateOf(fallbackCategory) }
    var note by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(pending.bankAccountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.confirmTransaction) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(AppStrings.from + " ${pending.sender}", style = MaterialTheme.typography.labelSmall)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        pending.rawMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.amountLabel) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                SingleChoiceSegmented(
                    options = listOf(AppStrings.expense, AppStrings.income),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelected = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
                )
                CategoryPicker(viewModel = viewModel, type = type, selected = category, onSelected = { category = it })
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
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(AppStrings.noteOptional) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    viewModel.confirmPendingSms(pending, amount, type, category.ifBlank { fallbackCategory }, note)
                    onDismiss()
                }
            }) { Text(AppStrings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
