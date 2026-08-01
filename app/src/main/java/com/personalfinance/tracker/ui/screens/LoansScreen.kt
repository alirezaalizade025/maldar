package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.personalfinance.tracker.ui.theme.AppCard
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.LoanEntity
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.fa
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius

@Composable
fun LoansScreen(viewModel: FinanceViewModel) {
    val loans by viewModel.loans.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showPayLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var selectedLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var editingLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var showMonthlySchedule by remember { mutableStateOf(false) }

    val activeLoans = loans.filter { !it.isPaid }
    val total = activeLoans.sumOf { it.principal }
    val totalRemaining = activeLoans.sumOf { it.remainingAmount }
    fun monthlyDue(loan: LoanEntity): Double {
        val installment = if (loan.installment > 0.0) loan.installment else loan.remainingAmount
        return installment.coerceAtMost(loan.remainingAmount)
    }
    fun paidThisMonth(loan: LoanEntity): Double = transactions
        .filter { it.loanId == loan.id && JalaliCalendar.isInJalaliMonth(it.dateMillis) }
        .sumOf { it.amount }
    fun isPaidThisMonth(loan: LoanEntity): Boolean = paidThisMonth(loan) >= monthlyDue(loan)
    val totalPaidThisMonth = transactions
        .filter { it.loanId != null && JalaliCalendar.isInJalaliMonth(it.dateMillis) }
        .sumOf { it.amount }
    val totalRemainingThisMonth = activeLoans.sumOf { (monthlyDue(it) - paidThisMonth(it)).coerceAtLeast(0.0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(AppStrings.loans, style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = AppStrings.addLoan) }
            }
            Text(
                AppStrings.loansHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppStrings.loansSummaryTotal, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(total) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppStrings.loanPaidThisMonthSummary, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(totalPaidThisMonth) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFF1B7A5A))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppStrings.loanRemainingThisMonthSummary, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(totalRemainingThisMonth) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFFE8604C))
                    }
                }
            }
        }

        // Monthly schedule section
        item {
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMonthlySchedule = !showMonthlySchedule }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(AppStrings.loanProjection, fontWeight = FontWeight.Medium)
                        Text(if (showMonthlySchedule) "▼" else "▶", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showMonthlySchedule) {
            item {
                MonthlyScheduleView(loans = loans, viewModel = viewModel, transactions = transactions)
            }
        }

        if (loans.isEmpty()) {
            item { Text(AppStrings.noLoans, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(loans) { loan ->
            val nextDueMillis = if (loan.isPaid) loan.dueDateMillis else JalaliCalendar.nextDueDateMillis(loan.payDayOfMonth)
            val daysLeft = JalaliCalendar.daysUntil(nextDueMillis)
            AppCard(
                modifier = Modifier.fillMaxWidth().clickable { selectedLoan = loan }) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loan.name, fontWeight = FontWeight.Bold)
                        if (loan.isPaid) {
                            Text(AppStrings.paid, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(AppStrings.due + ": " + JalaliCalendar.formatDate(nextDueMillis) + if (!loan.isPaid) "  (${if (daysLeft >= 0) "$daysLeft " + AppStrings.daysLeft else AppStrings.overdue})" else "",
                        style = MaterialTheme.typography.labelSmall)
                    Text(AppStrings.loanPayDay + ": " + loan.payDayOfMonth, style = MaterialTheme.typography.labelSmall)
                    loan.bankAccountId?.let { accountId ->
                        accounts.firstOrNull { it.id == accountId }?.let { account ->
                            Text(
                                "${AppStrings.loanAccount}: ${account.accountLabel}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (loan.installment > 0.0) {
                        Text(AppStrings.loanInstallment + ": " + Money.format2(loan.installment) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.labelSmall)
                        Text(AppStrings.loanMonthsLeft + ": " + viewModel.monthsRemaining(loan, isPaidThisMonth(loan)), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        if (isPaidThisMonth(loan)) AppStrings.loanInstallmentPaidThisMonth else AppStrings.loanNotPaidThisMonth,
                        color = if (isPaidThisMonth(loan)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(AppStrings.amount + ": " + Money.format2(loan.remainingAmount) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.bodyMedium)
                    if (loan.notes.isNotBlank()) Text(loan.notes, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (!loan.isPaid) {
                            Button(onClick = { showPayLoan = loan }) { Text(AppStrings.payLoan) }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(onClick = { editingLoan = loan }) { Text(AppStrings.edit) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { viewModel.deleteLoan(loan) }) { Text(AppStrings.delete) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showPayLoan != null) {
        PayLoanDialog(loan = showPayLoan!!, accounts = accounts, onDismiss = { showPayLoan = null },
            onPay = { amount, accountId -> viewModel.payLoan(showPayLoan!!, amount, accountId); showPayLoan = null })
    }

    if (selectedLoan != null) {
        LoanDetailDialog(loan = selectedLoan!!, viewModel = viewModel, onDismiss = { selectedLoan = null })
    }

    if (editingLoan != null) {
        EditLoanDialog(
            loan = editingLoan!!,
            accounts = accounts,
            onDismiss = { editingLoan = null },
            onSave = { updated ->
                viewModel.updateLoan(updated)
                editingLoan = null
            }
        )
    }

    if (showAdd) {
        AddLoanDialog(accounts = accounts, onDismiss = { showAdd = false }, onAdd = { name, principal, payDay, installment, totalMonths, accountId, reminderDays, notes ->
            viewModel.addLoan(name, principal, payDay, installment, totalMonths, accountId, reminderDays, notes)
            showAdd = false
        })
    }
}

@Composable
private fun EditLoanDialog(
    loan: LoanEntity,
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    onDismiss: () -> Unit,
    onSave: (LoanEntity) -> Unit
) {
    var name by remember(loan.id) { mutableStateOf(loan.name) }
    var remainingText by remember(loan.id) { mutableStateOf(Money.input(loan.remainingAmount)) }
    var payDayText by remember(loan.id) { mutableStateOf(loan.payDayOfMonth.toString()) }
    var installmentText by remember(loan.id) { mutableStateOf(if (loan.installment > 0.0) Money.input(loan.installment) else "") }
    var totalMonthsText by remember(loan.id) { mutableStateOf(if (loan.totalMonths > 0) loan.totalMonths.toString() else "") }
    var reminderDays by remember(loan.id) { mutableStateOf(loan.reminderDaysBefore) }
    var notes by remember(loan.id) { mutableStateOf(loan.notes) }
    var accountId by remember(loan.id) { mutableStateOf(loan.bankAccountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val reminderOptions = listOf(7, 3, 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.edit + " " + AppStrings.loans) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(AppStrings.loanName) })
                OutlinedTextField(
                    value = remainingText,
                    onValueChange = { remainingText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.remainder + " (" + AppStrings.moneyUnit + ")") },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                OutlinedTextField(
                    value = payDayText,
                    onValueChange = { payDayText = sanitizeNumberInput(it).takeWhile { c -> c.isDigit() } },
                    label = { Text(AppStrings.payDayOfMonth) }
                )
                OutlinedTextField(
                    value = installmentText,
                    onValueChange = { installmentText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.loanInstallment) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                OutlinedTextField(
                    value = totalMonthsText,
                    onValueChange = { totalMonthsText = sanitizeNumberInput(it).takeWhile { c -> c.isDigit() } },
                    label = { Text(AppStrings.loanTotalMonths) }
                )
                LoanAccountPicker(
                    accounts = accounts,
                    selectedAccountId = accountId,
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it },
                    onSelected = { accountId = it; accountMenuExpanded = false }
                )
                Text(AppStrings.remindDaysBefore, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminderOptions.forEach { days ->
                        FilterChip(
                            selected = reminderDays == days,
                            onClick = { reminderDays = days },
                            label = {
                                Text(
                                    when (days) {
                                        7 -> AppStrings.remind7Days
                                        1 -> AppStrings.remind1Day
                                        else -> AppStrings.remind3Days
                                    }
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(AppStrings.notesOptional) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val remaining = remainingText.toDoubleOrNull()
                val payDay = payDayText.toIntOrNull()
                val installment = installmentText.toDoubleOrNull() ?: 0.0
                val months = totalMonthsText.toIntOrNull() ?: loan.totalMonths
                if (name.isNotBlank() && remaining != null && payDay != null && payDay in 1..31) {
                    val updatedRemaining = remaining.coerceAtLeast(0.0)
                    val isPaid = updatedRemaining <= 0.0
                    val nextDue = if (isPaid) loan.dueDateMillis else JalaliCalendar.nextDueDateMillis(payDay)
                    onSave(
                        loan.copy(
                            name = name,
                            remainingAmount = updatedRemaining,
                            payDayOfMonth = payDay,
                            dueDateMillis = nextDue,
                            installment = installment,
                            totalMonths = months,
                            bankAccountId = accountId,
                            reminderDaysBefore = reminderDays,
                            notes = notes,
                            isPaid = isPaid
                        )
                    )
                }
            }) { Text(AppStrings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}

@Composable
private fun MonthlyScheduleView(loans: List<LoanEntity>, viewModel: FinanceViewModel, transactions: List<com.personalfinance.tracker.data.TransactionEntity>) {
    // Find the longest loan to determine max months to display
    val maxMonths = loans.filter { !it.isPaid }.maxOfOrNull { loan ->
        viewModel.monthsRemaining(loan, transactions.any { it.loanId == loan.id && JalaliCalendar.isInJalaliMonth(it.dateMillis) })
    } ?: 0
    
    Column(Modifier.fillMaxWidth()) {
        repeat(maxMonths.coerceAtMost(24)) { monthIndex ->
            val monthNum = monthIndex + 1
            val monthLabel = JalaliCalendar.monthLabel(Calendar.getInstance(), monthIndex)
            var monthTotal = 0.0
            val monthLoans = mutableListOf<String>()
            
            loans.filter { !it.isPaid }.forEach { loan ->
                if (monthNum <= viewModel.monthsRemaining(loan, transactions.any { it.loanId == loan.id && JalaliCalendar.isInJalaliMonth(it.dateMillis) })) {
                    val payment = if (loan.installment > 0.0) loan.installment else loan.remainingAmount
                    monthTotal += payment
                    monthLoans.add("${loan.name}: " + Money.format2(payment) + " " + AppStrings.moneyUnit)
                }
            }
            
            if (monthTotal > 0) {
                AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "$monthLabel: " + Money.format2(monthTotal) + " " + AppStrings.moneyUnit,
                            fontWeight = FontWeight.Bold
                        )
                        monthLoans.forEach { loan ->
                            Text("  - $loan", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayLoanDialog(
    loan: LoanEntity,
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    onDismiss: () -> Unit,
    onPay: (Double, Long?) -> Unit
) {
    var amountText by remember { mutableStateOf(Money.input(loan.remainingAmount)) }
    var accountId by remember(loan.id) { mutableStateOf(loan.bankAccountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.payLoan + " - " + loan.name) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.loanPaymentAmount) },
                    visualTransformation = ThousandsSeparatorTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == accountId }?.accountLabel ?: AppStrings.noneCash,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(AppStrings.bankAccount) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text(AppStrings.noneCash) }, onClick = {
                            accountId = null
                            accountMenuExpanded = false
                        })
                        accounts.forEach { account ->
                            DropdownMenuItem(text = { Text(account.accountLabel) }, onClick = {
                                accountId = account.id
                                accountMenuExpanded = false
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) onPay(amount, accountId)
            }) { Text(AppStrings.payLoan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}

@Composable
private fun LoanDetailDialog(loan: LoanEntity, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    val payments by produceState(initialValue = emptyList<com.personalfinance.tracker.data.TransactionEntity>(), loan.id) {
        value = viewModel.getLoanPayments(loan.id)
    }
    val currentMonthPaid = payments.any { JalaliCalendar.isInJalaliMonth(it.dateMillis) }
    // Projected remaining balance over the next months until payoff.
    val projection = remember(loan.id, loan.remainingAmount, currentMonthPaid) {
        val inst = if (loan.installment > 0.0) loan.installment else loan.remainingAmount
        val months = if (loan.installment > 0.0) viewModel.monthsRemaining(loan, currentMonthPaid) else 1
        (0..months.coerceAtMost(24)).scan(loan.remainingAmount) { acc, _ -> (acc - inst).coerceAtLeast(0.0) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loan.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(AppStrings.amount + ": " + Money.format2(loan.remainingAmount) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.bodyMedium)
                if (loan.installment > 0.0) {
                    Text(AppStrings.loanInstallment + ": " + Money.format2(loan.installment) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.labelSmall)
                    Text(AppStrings.loanMonthsLeft + ": " + viewModel.monthsRemaining(loan, currentMonthPaid), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(AppStrings.loanProjection, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LoanProjectionChart(projection = projection)
                }
                Text(AppStrings.loanLastPayment + ":", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (payments.isEmpty()) {
                    Text(AppStrings.loanNoPayment, style = MaterialTheme.typography.labelSmall)
                } else {
                    payments.take(10).forEach { p ->
                        Text("- " + Money.format2(p.amount) + " " + AppStrings.moneyUnit + "  " + JalaliCalendar.formatDate(p.dateMillis),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(AppStrings.close) } }
    )
}

@Composable
private fun LoanProjectionChart(projection: List<Double>) {
    if (projection.size < 2) return
    val maxVal = projection.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
        val sidePad = 8.dp.toPx()
        val groupW = (size.width - sidePad * 2) / projection.size
        val baseY = size.height - 6.dp.toPx()
        val top = 6.dp.toPx()
        val lineH = baseY - top
        val points = projection.mapIndexed { i, v ->
            val x = sidePad + i * groupW + groupW / 2
            val y = baseY - ((v / maxVal) * lineH).toFloat()
            Offset(x, y)
        }
        for (i in 1 until points.size) {
            drawLine(
                color = androidx.compose.ui.graphics.Color(0xFF2B6CB0),
                start = points[i - 1], end = points[i], strokeWidth = 3.dp.toPx()
            )
        }
        points.forEach { p ->
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF2B6CB0),
                topLeft = Offset(p.x - 3.dp.toPx(), p.y - 3.dp.toPx()),
                size = Size(6.dp.toPx(), 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
        }
    }
}

@Composable
private fun AddLoanDialog(
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, Double, Int, Double, Int, Long?, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var payDay by remember { mutableStateOf("") }
    var installment by remember { mutableStateOf("") }
    var totalMonths by remember { mutableStateOf("") }
    // Reminder is a fixed choice (not free input): 7, 3, or 1 day before due date.
    var reminderDays by remember { mutableStateOf(3) }
    val reminderOptions = listOf(7 to AppStrings.remind7Days, 3 to AppStrings.remind3Days, 1 to AppStrings.remind1Day)
    var notes by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.addLoan) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(AppStrings.loanName) })
                OutlinedTextField(
                    value = principal,
                    onValueChange = { principal = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.principal) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                OutlinedTextField(
                    value = installment,
                    onValueChange = { installment = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.loanInstallment) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                OutlinedTextField(
                    value = totalMonths,
                    onValueChange = { totalMonths = sanitizeNumberInput(it).takeWhile { c -> c.isDigit() } },
                    label = { Text(AppStrings.loanTotalMonths) }
                )
                OutlinedTextField(
                    value = payDay,
                    onValueChange = { payDay = sanitizeNumberInput(it).takeWhile { c -> c.isDigit() } },
                    label = { Text(AppStrings.payDayOfMonth) }
                )
                LoanAccountPicker(
                    accounts = accounts,
                    selectedAccountId = accountId,
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it },
                    onSelected = { accountId = it; accountMenuExpanded = false }
                )
                Text(AppStrings.remindDaysBefore, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminderOptions.forEach { (days, label) ->
                        FilterChip(
                            selected = reminderDays == days,
                            onClick = { reminderDays = days },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(AppStrings.notesOptional) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = principal.toDoubleOrNull()
                val day = payDay.toIntOrNull()
                val inst = installment.toDoubleOrNull() ?: 0.0
                val months = totalMonths.toIntOrNull() ?: 0
                if (name.isNotBlank() && amount != null && day != null && day in 1..31) {
                    onAdd(name, amount, day, inst, months, accountId, reminderDays, notes)
                }
            }) { Text(AppStrings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}

@Composable
private fun LoanAccountPicker(
    accounts: List<com.personalfinance.tracker.data.BankAccountEntity>,
    selectedAccountId: Long?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Long?) -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = accounts.firstOrNull { it.id == selectedAccountId }?.accountLabel ?: AppStrings.none,
            onValueChange = {},
            readOnly = true,
            label = { Text(AppStrings.loanAccount) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(text = { Text(AppStrings.none) }, onClick = { onSelected(null) })
            accounts.forEach { account ->
                DropdownMenuItem(text = { Text(account.accountLabel) }, onClick = { onSelected(account.id) })
            }
        }
    }
}
