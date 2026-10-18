package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.data.matchesLoanPayment
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme
import com.personalfinance.tracker.ui.design.components.AmountText
import com.personalfinance.tracker.ui.design.components.AmountTone
import com.personalfinance.tracker.ui.design.components.AppCard as DesignAppCard
import com.personalfinance.tracker.ui.design.components.AppCardStyle
import com.personalfinance.tracker.ui.design.components.MetricCard
import com.personalfinance.tracker.ui.design.components.rememberMonthlyBudget
import com.personalfinance.tracker.ui.design.components.monthlyBudgetItems
import com.personalfinance.tracker.ui.design.components.SectionHeader
import com.personalfinance.tracker.ui.design.components.TransactionRow
import com.personalfinance.tracker.ui.design.components.WarningBanner
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.fa
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
fun DashboardScreen(viewModel: FinanceViewModel, onGoToConfirm: () -> Unit, onGoToReports: () -> Unit = {}) {
    MaldarDesignTheme {
        DashboardContent(viewModel, onGoToConfirm, onGoToReports)
    }
}

@Composable
private fun DashboardContent(viewModel: FinanceViewModel, onGoToConfirm: () -> Unit, onGoToReports: () -> Unit) {
    var showSearch by remember { mutableStateOf(false) }
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val categories by viewModel.expenseCategories.collectAsState()
    val monthlyBudget = rememberMonthlyBudget(transactions, categories.map { it.name })
    val pending by viewModel.pendingSms.collectAsState()
    val reviewed by viewModel.reviewedSms.collectAsState()

    var monthIncome by remember { mutableStateOf(0.0) }
    var monthExpense by remember { mutableStateOf(0.0) }
    var editingTx by remember { mutableStateOf<com.personalfinance.tracker.data.TransactionEntity?>(null) }
    var editingTxForSms by remember { mutableStateOf<com.personalfinance.tracker.data.PendingSmsEntity?>(null) }
    var totalBalance by remember { mutableStateOf(0.0) }
    LaunchedEffect(transactions, accounts, monthlyBudget.startMillis) {
        val (inc, exp) = viewModel.monthlyIncomeExpense(0)
        monthIncome = inc; monthExpense = exp
        totalBalance = viewModel.totalAccountBalance()
    }

    val monthLoanPaid = transactions.filter { tx ->
        JalaliCalendar.isInJalaliMonth(tx.dateMillis) && loans.any { tx.matchesLoanPayment(it, loans) }
    }.sumOf { it.amount }

    val reportTransactions = com.personalfinance.tracker.data.reportingTransactions(transactions)
    val allIncome = reportTransactions.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val allExpense = reportTransactions.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }

    val listState = rememberLazyListState()
    var visibleCount by remember { mutableIntStateOf(15) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to transactions.size
        }.collect { (lastVisible, total) ->
            if (total > visibleCount && lastVisible >= visibleCount - 4) {
                visibleCount = (visibleCount + 20).coerceAtMost(total)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = MaldarDesign.spacing.lg,
            end = MaldarDesign.spacing.lg,
            top = MaldarDesign.spacing.lg,
            bottom = MaldarDesign.spacing.section
        ),
        verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.md)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(AppStrings.overview, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    JalaliCalendar.formatDate(System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }

        item { OutlinedButton(onClick = { showSearch = true }, modifier = Modifier.fillMaxWidth()) { Text("جست‌وجوی تراکنش‌ها") } }

        // First-launch onboarding: guide the user when the app is completely empty.
        if (accounts.isEmpty() && transactions.isEmpty()) {
            item {
                DesignAppCard(
                    style = AppCardStyle.OUTLINED,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)) {
                        Text(AppStrings.onboardingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(AppStrings.onboardingBody, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (pending.isNotEmpty()) {
            item {
                WarningBanner(
                    message = AppStrings.pendingSms.fa(pending.size),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = onGoToConfirm,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = AppStrings.confirmSms
                        )
                )
            }
            item {
                SectionHeader(
                    title = AppStrings.unreadSms,
                    actionLabel = AppStrings.confirmEdit,
                    onAction = onGoToConfirm
                )
            }
            items(pending) { p ->
                DesignAppCard(
                    style = AppCardStyle.OUTLINED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { editingTxForSms = p },
                            role = androidx.compose.ui.semantics.Role.Button
                        )
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.xs)) {
                            Text(
                                "${p.parsedType?.name ?: AppStrings.unknown} • ${p.parsedAmount?.let { Money.format2(it) + " " + AppStrings.moneyUnit } ?: AppStrings.amountUnclear}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
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
                DesignAppCard(style = AppCardStyle.OUTLINED, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(AppStrings.reviewedSms.fa(reviewed.size), style = MaterialTheme.typography.titleMedium)
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
            DesignAppCard(style = AppCardStyle.HERO, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(AppStrings.totalBalance, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    val animatedBalance by animateFloatAsState(
                        targetValue = totalBalance.toFloat(),
                        animationSpec = tween(durationMillis = 400),
                        label = "balance"
                    )
                    AmountText(
                        amount = Money.format2(animatedBalance.toDouble()),
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)
            ) {
                MetricCard(
                    label = AppStrings.monthIncome,
                    value = Money.format2(monthIncome),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tone = AmountTone.POSITIVE,
                    modifier = Modifier.width(148.dp)
                )
                MetricCard(
                    label = AppStrings.monthExpense,
                    value = Money.format2(monthExpense),
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    tone = AmountTone.NEGATIVE,
                    modifier = Modifier.width(148.dp)
                )
                MetricCard(
                    label = AppStrings.loanPaidThisMonth,
                    value = Money.format2(monthLoanPaid),
                    icon = Icons.Filled.Payments,
                    modifier = Modifier.width(148.dp)
                )
            }
        }

        item {
            SectionHeader(
                title = AppStrings.recentTransactions,
                actionLabel = AppStrings.reports,
                onAction = onGoToReports
            )
        }

        item {
            DesignAppCard(style = AppCardStyle.OUTLINED, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(AppStrings.transactionCount.fa(transactions.size), style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(allIncome) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = MaldarDesign.colors.positive)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(AppStrings.allTransactionsSum, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(allExpense) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = MaldarDesign.colors.negative)
                    }
                }
            }
        }

        monthlyBudgetItems(monthlyBudget)

        if (transactions.isEmpty()) {
            item {
                com.personalfinance.tracker.ui.design.components.EmptyState(
                    title = AppStrings.recentTransactions,
                    message = AppStrings.noTransactions,
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaldarDesign.spacing.xxl)
                )
            }
        }

        items(transactions.take(visibleCount)) { tx ->
            DesignAppCard(
                style = AppCardStyle.OUTLINED,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { editingTx = tx },
                        role = androidx.compose.ui.semantics.Role.Button
                    ),
                contentPadding = PaddingValues(0.dp)
            ) {
                TransactionRow(
                    title = tx.category,
                    amount = Money.format2(reportTransactions.firstOrNull { it.id == tx.id }?.amount ?: tx.amount),
                    metadata = buildString {
                        append(JalaliCalendar.formatDateTime(tx.dateMillis))
                        if (tx.parentTransactionId != null) append(" • بازپرداخت مشترک")
                        if (transactions.any { it.parentTransactionId == tx.id }) append(" • خالص هزینهٔ مشترک")
                        tx.balanceAfter?.let {
                            append(" • ${AppStrings.remainedAfter}: ${Money.format2(it)} ${AppStrings.moneyUnit}")
                        }
                    },
                    tone = if (tx.type == TxType.INCOME) AmountTone.POSITIVE else AmountTone.NEGATIVE
                )
            }
        }

        if (visibleCount < transactions.size) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = MaldarDesign.spacing.md), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showSearch) TransactionSearchDialog(viewModel) { showSearch = false }

    transactions.firstOrNull { it.id == editingTx?.id }?.let { tx ->
        EditTransactionDialog(
            tx = tx,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { editingTx = null }
        )
    }

    editingTxForSms?.let { p ->
        SmsTransactionDialog(pending = p, accounts = accounts, viewModel = viewModel, onDismiss = { editingTxForSms = null })
    }
}

@Composable
internal fun EditTransactionDialog(
    tx: com.personalfinance.tracker.data.TransactionEntity,
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val allTransactions by viewModel.transactions.collectAsState()
    var showShare by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(Money.input(tx.amount)) }
    var type by remember { mutableStateOf(tx.type) }
    var category by remember { mutableStateOf(tx.category) }
    var note by remember { mutableStateOf(tx.note) }
    var selectedAccountId by remember { mutableStateOf(tx.bankAccountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var selectedToAccountId by remember { mutableStateOf(tx.toAccountId) }
    var toAccountMenuExpanded by remember { mutableStateOf(false) }
    var remainderText by remember { mutableStateOf(tx.balanceAfter?.let(Money::input) ?: "") }
    var transactionDateMillis by remember { mutableStateOf(tx.dateMillis) }
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
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmented(
                    options = listOf(AppStrings.expense, AppStrings.income, AppStrings.cardToCard),
                    selectedIndex = when (type) { TxType.EXPENSE -> 0; TxType.INCOME -> 1; TxType.CARD_TO_CARD -> 2 },
                    onSelected = {
                        type = when (it) { 0 -> TxType.EXPENSE; 1 -> TxType.INCOME; else -> TxType.CARD_TO_CARD }
                        category = if (type == TxType.CARD_TO_CARD) AppStrings.cardToCard else ""
                    }
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.amountLabel) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                if (type != TxType.CARD_TO_CARD) {
                    CategoryPicker(viewModel = viewModel, type = type, selected = category, onSelected = { category = it })
                }
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
                if (type == TxType.CARD_TO_CARD) {
                    ExposedDropdownMenuBox(expanded = toAccountMenuExpanded, onExpandedChange = { toAccountMenuExpanded = it }) {
                        OutlinedTextField(
                            value = accounts.firstOrNull { it.id == selectedToAccountId }?.accountLabel ?: AppStrings.selectDestinationAccount,
                            onValueChange = {}, readOnly = true,
                            label = { Text(AppStrings.toAccount) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = toAccountMenuExpanded, onDismissRequest = { toAccountMenuExpanded = false }) {
                            accounts.filter { it.id != selectedAccountId }.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.accountLabel) }, onClick = { selectedToAccountId = acc.id; toAccountMenuExpanded = false })
                            }
                        }
                    }
                }
                TransactionDateTimeFields(transactionDateMillis) { transactionDateMillis = it }

                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(AppStrings.noteOptional) })
                ShareDescription(tx, allTransactions)
                if (tx.type != TxType.CARD_TO_CARD && tx.loanId == null) {
                    OutlinedButton(onClick = { showShare = true }) { Text("هزینهٔ مشترک / بازپرداخت‌ها") }
                }
                if (selectedAccountId != null) {
                    OutlinedTextField(
                        value = remainderText,
                        onValueChange = { remainderText = sanitizeNumberInput(it) },
                        label = { Text(AppStrings.balanceAfter + " (" + AppStrings.optional + ")") },
                        visualTransformation = ThousandsSeparatorTransformation()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                val remainder = remainderText.toDoubleOrNull()
                if (amount != null && amount > 0 && category.isNotBlank()) {
                    viewModel.updateTransaction(
                        tx.copy(
                            amount = amount,
                            type = type,
                            category = category,
                            note = note,
                            bankAccountId = selectedAccountId,
                            dateMillis = transactionDateMillis,
                            toAccountId = if (type == TxType.CARD_TO_CARD) selectedToAccountId else null,
                            loanId = tx.loanId.takeIf { type == TxType.EXPENSE },
                            balanceAfter = if (selectedAccountId != null) remainder else null
                        )
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

    if (showShare) ShareTransactionDialog(tx.id, viewModel) { showShare = false }

}
