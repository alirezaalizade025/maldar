package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.PendingSmsEntity
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
fun SmsConfirmationScreen(viewModel: FinanceViewModel) {
    val pending by viewModel.pendingSms.collectAsState()
    var editing by remember { mutableStateOf<PendingSmsEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Confirm SMS Transactions", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Detected from your bank SMS. Review and confirm before they're saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))

        if (pending.isEmpty()) {
            Text("Nothing pending right now.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pending) { p ->
                Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "${p.parsedType?.name ?: "Unknown"} • ${p.parsedAmount?.let { "₹%,.2f".format(it) } ?: "amount unclear"}",
                            fontWeight = FontWeight.Bold
                        )
                        Text("From: ${p.sender}", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(p.rawMessage, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Button(onClick = { editing = p }) { Text("Confirm / Edit") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.rejectPendingSms(p) }) { Text("Ignore") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { p ->
        ConfirmDialog(viewModel = viewModel, pending = p, onDismiss = { editing = null }, onConfirm = { amount, type, category, note ->
            viewModel.confirmPendingSms(p, amount, type, category, note)
            editing = null
        })
    }
}

@Composable
private fun ConfirmDialog(
    viewModel: FinanceViewModel,
    pending: PendingSmsEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double, TxType, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf(pending.parsedAmount?.toString() ?: "") }
    var type by remember { mutableStateOf(pending.parsedType ?: TxType.EXPENSE) }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") }
                )
                SingleChoiceSegmented(
                    options = listOf("Expense", "Income"),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelected = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME; category = "" }
                )
                CategoryPicker(
                    viewModel = viewModel,
                    type = type,
                    selected = category,
                    onSelected = { category = it }
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) onConfirm(amount, type, category, note)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
