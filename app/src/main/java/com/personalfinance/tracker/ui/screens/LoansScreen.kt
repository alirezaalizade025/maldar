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
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.LoanEntity
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius

@Composable
fun LoansScreen(viewModel: FinanceViewModel) {
    val loans by viewModel.loans.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showPayLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var selectedLoan by remember { mutableStateOf<LoanEntity?>(null) }

    val total = loans.sumOf { it.remainingAmount }
    // Amount whose pay-day has already passed this Jalali month (due so far).
    val jNow = JalaliCalendar.fromGregorian(Calendar.getInstance())
    val dueSoFar = loans.filter { !it.isPaid && it.payDayOfMonth <= jNow.day }
        .sumOf { it.remainingAmount }
    val remainNext = (total - dueSoFar).coerceAtLeast(0.0)
    val totalMonthsRemaining = loans.filter { !it.isPaid }.sumOf { viewModel.monthsRemaining(it) }

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
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppStrings.loansSummaryTotal, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(total) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppStrings.loansSummaryDue, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(dueSoFar) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFFE8604C))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppStrings.loansSummaryRemain, style = MaterialTheme.typography.labelSmall)
                        Text(Money.format2(remainNext) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFF1B7A5A))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(AppStrings.loansSummaryMonths, style = MaterialTheme.typography.labelSmall)
                        Text(AppStrings.monthsFormat.format(totalMonthsRemaining), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (loans.isEmpty()) {
            item { Text(AppStrings.noLoans, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(loans) { loan ->
            val daysLeft = ((loan.dueDateMillis - System.currentTimeMillis()) / 86_400_000L)
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clickable { selectedLoan = loan }) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loan.name, fontWeight = FontWeight.Bold)
                        if (loan.isPaid) {
                            Text(AppStrings.paid, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(AppStrings.due + ": " + JalaliCalendar.formatDate(loan.dueDateMillis) + if (!loan.isPaid) "  (${if (daysLeft >= 0) "$daysLeft " + AppStrings.daysLeft else AppStrings.overdue})" else "",
                        style = MaterialTheme.typography.labelSmall)
                    Text(AppStrings.loanPayDay + ": " + loan.payDayOfMonth, style = MaterialTheme.typography.labelSmall)
                    if (loan.installment > 0.0) {
                        Text(AppStrings.loanInstallment + ": " + Money.format2(loan.installment) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.labelSmall)
                        Text(AppStrings.loanMonthsLeft + ": " + viewModel.monthsRemaining(loan), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(AppStrings.amount + ": " + Money.format2(loan.remainingAmount) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.bodyMedium)
                    if (loan.notes.isNotBlank()) Text(loan.notes, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (!loan.isPaid) {
                            Button(onClick = { showPayLoan = loan }) { Text(AppStrings.payLoan) }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(onClick = { viewModel.deleteLoan(loan) }) { Text(AppStrings.delete) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showPayLoan != null) {
        PayLoanDialog(loan = showPayLoan!!, onDismiss = { showPayLoan = null },
            onPay = { amount -> viewModel.payLoan(showPayLoan!!, amount); showPayLoan = null })
    }

    if (selectedLoan != null) {
        LoanDetailDialog(loan = selectedLoan!!, viewModel = viewModel, onDismiss = { selectedLoan = null })
    }

    if (showAdd) {
        AddLoanDialog(onDismiss = { showAdd = false }, onAdd = { name, principal, payDay, installment, totalMonths, reminderDays, notes ->
            viewModel.addLoan(name, principal, payDay, installment, totalMonths, reminderDays, notes)
            showAdd = false
        })
    }
}

@Composable
private fun PayLoanDialog(loan: LoanEntity, onDismiss: () -> Unit, onPay: (Double) -> Unit) {
    var amountText by remember { mutableStateOf(loan.remainingAmount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.payLoan + " - " + loan.name) },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(AppStrings.loanPaymentAmount) },
                visualTransformation = ThousandsSeparatorTransformation()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) onPay(amount)
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
    // Projected remaining balance over the next months until payoff.
    val projection = remember(loan.id) {
        val inst = if (loan.installment > 0.0) loan.installment else loan.remainingAmount
        val months = if (loan.installment > 0.0) viewModel.monthsRemaining(loan) else 1
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
                    Text(AppStrings.loanMonthsLeft + ": " + viewModel.monthsRemaining(loan), style = MaterialTheme.typography.labelSmall)
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
private fun AddLoanDialog(onDismiss: () -> Unit, onAdd: (String, Double, Int, Double, Int, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var payDay by remember { mutableStateOf("") }
    var installment by remember { mutableStateOf("") }
    var totalMonths by remember { mutableStateOf("") }
    // Reminder is a fixed choice (not free input): 7, 3, or 1 day before due date.
    var reminderDays by remember { mutableStateOf(3) }
    val reminderOptions = listOf(7 to AppStrings.remind7Days, 3 to AppStrings.remind3Days, 1 to AppStrings.remind1Day)
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.addLoan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(AppStrings.loanName) })
                OutlinedTextField(
                    value = principal,
                    onValueChange = { principal = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(AppStrings.principal) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                OutlinedTextField(
                    value = installment,
                    onValueChange = { installment = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(AppStrings.loanInstallment) },
                    visualTransformation = ThousandsSeparatorTransformation()
                )
                OutlinedTextField(
                    value = totalMonths,
                    onValueChange = { totalMonths = it.filter { c -> c.isDigit() } },
                    label = { Text(AppStrings.loanTotalMonths) }
                )
                OutlinedTextField(
                    value = payDay,
                    onValueChange = { payDay = it.filter { c -> c.isDigit() } },
                    label = { Text(AppStrings.payDayOfMonth) }
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
                    onAdd(name, amount, day, inst, months, reminderDays, notes)
                }
            }) { Text(AppStrings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
