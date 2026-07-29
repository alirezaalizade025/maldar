package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxSource
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.CrashLogger
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

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
    var remainderText by remember { mutableStateOf("") }
    var transactionDateMillis by remember { mutableStateOf<Long?>(null) }
    var sourceSmsBody by remember { mutableStateOf<String?>(null) }
    var savedCount by remember { mutableStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val smsSenderIds = remember(senders, accountId) {
        if (accountId == null) senders.map { it.senderId }
        else senders.filter { it.bankAccountId == accountId }.map { it.senderId }
    }
    val smsAccount = accounts.firstOrNull { it.id == accountId }
    val smsAccountReady = accountId == null || smsAccount != null
    val smsAccountLast4 = smsAccount?.accountLast4.orEmpty()

    // When opened from a bank SMS, pre-fill the amount/type/account/note from it.
    LaunchedEffect(accountId, smsDate, smsSenderIds, smsAccountReady, smsAccountLast4) {
        if (smsDate != null && smsSenderIds.isNotEmpty() && smsAccountReady) {
            val sms = SmsInboxReader.findSmsByDate(context, smsSenderIds, smsDate, smsAccountLast4)
            sms?.let {
                if (it.amount != null) amountText = Money.input(it.amount)
                it.type?.let { t -> type = t }
                selectedAccountId = accountId
                if (it.balanceAfter != null) remainderText = Money.input(it.balanceAfter)
                transactionDateMillis = it.dateMillis
                sourceSmsBody = it.body
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
            options = listOf(AppStrings.expense, AppStrings.income, AppStrings.cardToCard),
            selectedIndex = when(type) {
                TxType.EXPENSE -> 0
                TxType.INCOME -> 1
                TxType.CARD_TO_CARD -> 2
            },
            onSelected = {
                type = when(it) {
                    0 -> TxType.EXPENSE
                    1 -> TxType.INCOME
                    else -> TxType.CARD_TO_CARD
                }
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

        // Show remainder field when account is selected
        if (selectedAccountId != null) {
            OutlinedTextField(
                value = remainderText,
                onValueChange = { remainderText = sanitizeNumberInput(it) },
                label = { Text(AppStrings.balanceAfter + " (" + AppStrings.optional + ")") },
                visualTransformation = ThousandsSeparatorTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                AppStrings.balanceAfter + ": " + (accounts.firstOrNull { it.id == selectedAccountId }?.balance?.let { Money.format2(it) } ?: "—") + " " + AppStrings.moneyUnit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

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

                    // Explicit/SMS balance is trusted. A blank balance is passed
                    // as null so Room calculates it from the latest account row
                    // inside the same atomic transaction as the insert.
                    val remainder = if (remainderText.isNotBlank()) {
                        remainderText.toDoubleOrNull()?.let { if (rialMode) it / 10.0 else it }
                    } else {
                        null
                    }

                    val smsBody = sourceSmsBody
                    isSaving = true
                    scope.launch {
                        val result = runCatching {
                            viewModel.addTransaction(
                                amount = stored,
                                type = type,
                                category = category.ifBlank { "سایر" },
                                note = note,
                                bankAccountId = selectedAccountId,
                                dateMillis = transactionDateMillis ?: System.currentTimeMillis(),
                                loanId = selectedLoanId,
                                balanceAfter = remainder,
                                source = if (smsBody != null) TxSource.SMS else TxSource.MANUAL,
                                rawSms = smsBody
                            )
                        }
                        isSaving = false
                        result.onSuccess { insertedId ->
                            if (insertedId <= 0L) {
                                confirmationMessage = AppStrings.transactionSaveFailed
                                return@onSuccess
                            }
                            confirmationMessage = AppStrings.saved
                            savedCount++
                            amountText = ""
                            note = ""
                            remainderText = ""
                            selectedLoanId = null
                            transactionDateMillis = null
                            sourceSmsBody = null
                        }.onFailure { error ->
                            CrashLogger.log("transaction: database insert failed", error)
                            confirmationMessage = AppStrings.transactionSaveFailed
                        }
                    }
                } else {
                    confirmationMessage = AppStrings.invalidAmount
                }
            },
            enabled = !isSaving,
            interactionSource = saveInteraction,
            modifier = Modifier.fillMaxWidth().scale(saveScale)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(AppStrings.save)
            }
        }

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
