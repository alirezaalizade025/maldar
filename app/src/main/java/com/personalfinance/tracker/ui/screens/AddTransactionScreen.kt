package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
fun AddTransactionScreen(
    viewModel: FinanceViewModel,
    accountId: Long? = null,
    smsDate: Long? = null,
    onContinueToList: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val accounts by viewModel.bankAccounts.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val senders by viewModel.smsSenders.collectAsState()

    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }
    // false = Toman (stored unit), true = Rial (entered value / 10 to convert)
    var rialMode by remember { mutableStateOf(false) }
    var selectedLoanId by remember { mutableStateOf<Long?>(null) }
    var loanMenuExpanded by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // When opened from a bank SMS, pre-fill the amount/type/account/note from it.
    LaunchedEffect(accountId, smsDate) {
        if (accountId != null && smsDate != null) {
            val senderIds = senders.filter { it.bankAccountId == accountId }.map { it.senderId }
            val sms = SmsInboxReader.findSmsByDate(context, senderIds, smsDate)
            sms?.let {
                if (it.amount != null) amountText = it.amount.toLong().toString()
                it.type?.let { t -> type = t }
                selectedAccountId = accountId
                note = it.body
            }
        }
    }

    LaunchedEffect(confirmationMessage) {
        confirmationMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            confirmationMessage = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(AppStrings.addTransaction, style = MaterialTheme.typography.headlineMedium)

        SingleChoiceSegmented(
            options = listOf(AppStrings.expense, AppStrings.income),
            selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
            onSelected = {
                type = if (it == 0) TxType.EXPENSE else TxType.INCOME
                category = "" // let CategoryPicker re-default to the first category of the new type
            }
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = sanitizeNumberInput(it) },
            label = { Text(AppStrings.amountLabel) },
            visualTransformation = ThousandsSeparatorTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(AppStrings.unit, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = !rialMode,
                onClick = { rialMode = false },
                label = { Text(AppStrings.toman) }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = rialMode,
                onClick = { rialMode = true },
                label = { Text(AppStrings.rial) }
            )
        }

        CategoryPicker(
            viewModel = viewModel,
            type = type,
            selected = category,
            onSelected = { category = it }
        )

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

        ExposedDropdownMenuBox(expanded = loanMenuExpanded, onExpandedChange = { loanMenuExpanded = it }) {
            OutlinedTextField(
                value = loans.firstOrNull { it.id == selectedLoanId }?.name ?: AppStrings.relatedToLoan,
                onValueChange = {}, readOnly = true,
                label = { Text(AppStrings.relatedToLoan + " (" + AppStrings.optional + ")") },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = loanMenuExpanded, onDismissRequest = { loanMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(AppStrings.none) }, onClick = { selectedLoanId = null; loanMenuExpanded = false })
                loans.filter { !it.isPaid }.forEach { loan ->
                    DropdownMenuItem(text = { Text(loan.name) }, onClick = { selectedLoanId = loan.id; loanMenuExpanded = false })
                }
            }
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text(AppStrings.noteOptional) },
            modifier = Modifier.fillMaxWidth()
        )

        val saveInteraction = remember { MutableInteractionSource() }
        val pressed by saveInteraction.collectIsPressedAsState()
        val saveScale by animateFloatAsState(
            targetValue = if (pressed) 0.97f else 1f,
            animationSpec = tween(durationMillis = 120),
            label = "saveScale"
        )
        Button(
            onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val stored = if (rialMode) amount / 10.0 else amount
                    viewModel.addTransaction(stored, type, category, note, selectedAccountId, loanId = selectedLoanId)
                    confirmationMessage = AppStrings.saved
                    savedCount++
                    amountText = ""; note = ""; selectedLoanId = null
                } else {
                    confirmationMessage = AppStrings.invalidAmount
                }
            },
            interactionSource = saveInteraction,
            modifier = Modifier.fillMaxWidth().scale(saveScale)
        ) { Text(AppStrings.save) }

        if (onContinueToList != null) {
            if (savedCount > 0) {
                OutlinedButton(onClick = onContinueToList, modifier = Modifier.fillMaxWidth()) {
                    Text(AppStrings.continueToList)
                }
            } else {
                Text(AppStrings.smsAddHint, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    }
}

@Composable
fun SingleChoiceSegmented(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelected(index) },
                label = { Text(label) },
                modifier = Modifier.weight(1f).padding(end = if (index != options.lastIndex) 8.dp else 0.dp)
            )
        }
    }
}
