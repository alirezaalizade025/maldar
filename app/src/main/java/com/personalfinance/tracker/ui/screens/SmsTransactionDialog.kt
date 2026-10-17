package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.BankAccountEntity
import com.personalfinance.tracker.data.PendingSmsEntity
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
internal fun SmsTransactionDialog(
    pending: PendingSmsEntity,
    accounts: List<BankAccountEntity>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var amountText by remember(pending.id) { mutableStateOf(pending.parsedAmount?.let(Money::input) ?: "") }
    var type by remember(pending.id) { mutableStateOf(pending.parsedType ?: TxType.EXPENSE) }
    var category by remember(pending.id) { mutableStateOf("سایر") }
    var note by remember(pending.id) { mutableStateOf("") }
    var sourceId by remember(pending.id) { mutableStateOf(pending.bankAccountId) }
    var destinationId by remember(pending.id) { mutableStateOf<Long?>(null) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }
    var remainderText by remember(pending.id) { mutableStateOf(pending.parsedBalance?.let(Money::input) ?: "") }
    var dateMillis by remember(pending.id) { mutableLongStateOf(pending.timestampMillis) }
    val amount = amountText.toDoubleOrNull()
    val transfer = type == TxType.CARD_TO_CARD
    val validAccounts = if (transfer) {
        sourceId != destinationId && accounts.any { it.id == sourceId } && accounts.any { it.id == destinationId }
    } else sourceId == null || accounts.any { it.id == sourceId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.confirmTransaction) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.amountLabel) },
                    visualTransformation = ThousandsSeparatorTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmented(
                    options = listOf(AppStrings.expense, AppStrings.income, AppStrings.cardToCard),
                    selectedIndex = when (type) { TxType.EXPENSE -> 0; TxType.INCOME -> 1; TxType.CARD_TO_CARD -> 2 },
                    onSelected = {
                        type = when (it) { 0 -> TxType.EXPENSE; 1 -> TxType.INCOME; else -> TxType.CARD_TO_CARD }
                        category = if (type == TxType.CARD_TO_CARD) AppStrings.cardToCard else "سایر"
                    }
                )
                if (!transfer) CategoryPicker(viewModel, type, category) { category = it }
                ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { sourceExpanded = it }) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == sourceId }?.accountLabel ?: AppStrings.noneCash,
                        onValueChange = {}, readOnly = true,
                        label = { Text(if (transfer) AppStrings.fromAccount else AppStrings.bankAccount) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sourceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                        if (!transfer) DropdownMenuItem(text = { Text(AppStrings.noneCash) }, onClick = { sourceId = null; sourceExpanded = false })
                        accounts.forEach { account ->
                            DropdownMenuItem(text = { Text(account.accountLabel) }, onClick = {
                                sourceId = account.id
                                if (destinationId == sourceId) destinationId = null
                                sourceExpanded = false
                            })
                        }
                    }
                }
                if (transfer) {
                    ExposedDropdownMenuBox(expanded = destinationExpanded, onExpandedChange = { destinationExpanded = it }) {
                        OutlinedTextField(
                            value = accounts.firstOrNull { it.id == destinationId }?.accountLabel ?: AppStrings.selectDestinationAccount,
                            onValueChange = {}, readOnly = true,
                            label = { Text(AppStrings.toAccount) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(destinationExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = destinationExpanded, onDismissRequest = { destinationExpanded = false }) {
                            accounts.filter { it.id != sourceId }.forEach { account ->
                                DropdownMenuItem(text = { Text(account.accountLabel) }, onClick = {
                                    destinationId = account.id
                                    destinationExpanded = false
                                })
                            }
                        }
                    }
                }
                TransactionDateTimeFields(dateMillis) { dateMillis = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(AppStrings.noteOptional) }, modifier = Modifier.fillMaxWidth())
                if (!transfer && sourceId != null) {
                    OutlinedTextField(
                        value = remainderText, onValueChange = { remainderText = sanitizeNumberInput(it) },
                        label = { Text(AppStrings.balanceAfter + " (" + AppStrings.optional + ")") },
                        visualTransformation = ThousandsSeparatorTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(AppStrings.from + " ${pending.sender}", style = MaterialTheme.typography.labelSmall)
                Text(pending.rawMessage, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && amount.isFinite() && amount > 0.0 && validAccounts, onClick = {
                viewModel.confirmPendingSms(
                    pending = pending.copy(bankAccountId = sourceId), finalAmount = requireNotNull(amount), type = type,
                    category = if (transfer) AppStrings.cardToCard else category.ifBlank { "سایر" }, note = note,
                    finalBalanceAfter = if (!transfer && sourceId != null) remainderText.toDoubleOrNull() else null,
                    toAccountId = if (transfer) destinationId else null, dateMillis = dateMillis
                )
                onDismiss()
            }) { Text(AppStrings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
