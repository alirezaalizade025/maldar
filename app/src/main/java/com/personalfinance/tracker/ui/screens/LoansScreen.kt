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
        AddLoanDialog(onDismiss = { showAdd = false }, onAdd = { name, principal, payDay, reminderDays, notes ->
            viewModel.addLoan(name, principal, payDay, reminderDays, notes)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loan.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(AppStrings.amount + ": " + Money.format2(loan.remainingAmount) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.bodyMedium)
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
private fun AddLoanDialog(onDismiss: () -> Unit, onAdd: (String, Double, Int, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var payDay by remember { mutableStateOf("") }
    var reminderDays by remember { mutableStateOf("3") }
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
                    value = payDay,
                    onValueChange = { payDay = it.filter { c -> c.isDigit() } },
                    label = { Text(AppStrings.payDayOfMonth) }
                )
                OutlinedTextField(
                    value = reminderDays,
                    onValueChange = { reminderDays = it.filter { c -> c.isDigit() } },
                    label = { Text(AppStrings.remindDaysBefore) }
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(AppStrings.notesOptional) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = principal.toDoubleOrNull()
                val day = payDay.toIntOrNull()
                val reminder = reminderDays.toIntOrNull() ?: 3
                if (name.isNotBlank() && amount != null && day != null && day in 1..31) {
                    onAdd(name, amount, day, reminder, notes)
                }
            }) { Text(AppStrings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
