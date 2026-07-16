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
import com.personalfinance.tracker.data.LoanEntity
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.util.*

@Composable
fun LoansScreen(viewModel: FinanceViewModel) {
    val loans by viewModel.loans.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

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

        if (loans.isEmpty()) {
            item { Text(AppStrings.noLoans, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(loans) { loan ->
            val daysLeft = ((loan.dueDateMillis - System.currentTimeMillis()) / 86_400_000L)
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loan.name, fontWeight = FontWeight.Bold)
                        if (loan.isPaid) {
                            Text(AppStrings.paid, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(AppStrings.due + ": " + JalaliCalendar.formatDate(loan.dueDateMillis) + if (!loan.isPaid) "  (${if (daysLeft >= 0) "$daysLeft " + AppStrings.daysLeft else AppStrings.overdue})" else "",
                        style = MaterialTheme.typography.labelSmall)
                    Text(AppStrings.amount + ": " + Money.format2(loan.remainingAmount) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.bodyMedium)
                    if (loan.notes.isNotBlank()) Text(loan.notes, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (!loan.isPaid) {
                            Button(onClick = { viewModel.markLoanPaid(loan) }) { Text(AppStrings.markPaid) }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(onClick = { viewModel.deleteLoan(loan) }) { Text(AppStrings.delete) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showAdd) {
        AddLoanDialog(onDismiss = { showAdd = false }, onAdd = { name, principal, dueMillis, reminderDays, notes ->
            viewModel.addLoan(name, principal, dueMillis, reminderDays, notes)
            showAdd = false
        })
    }
}

@Composable
private fun AddLoanDialog(onDismiss: () -> Unit, onAdd: (String, Double, Long, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var daysFromNow by remember { mutableStateOf("30") }
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
                    label = { Text(AppStrings.principal) }
                )
                OutlinedTextField(
                    value = daysFromNow,
                    onValueChange = { daysFromNow = it.filter { c -> c.isDigit() } },
                    label = { Text(AppStrings.dueInDays) }
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
                val days = daysFromNow.toIntOrNull()
                val reminder = reminderDays.toIntOrNull() ?: 3
                if (name.isNotBlank() && amount != null && days != null) {
                    val due = System.currentTimeMillis() + days * 86_400_000L
                    onAdd(name, amount, due, reminder, notes)
                }
            }) { Text(AppStrings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
