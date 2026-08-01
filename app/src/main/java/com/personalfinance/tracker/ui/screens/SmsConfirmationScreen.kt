package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.PendingSmsEntity
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme
import com.personalfinance.tracker.ui.design.components.EmptyState
import com.personalfinance.tracker.ui.design.components.PendingTransactionCard
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.fa
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
fun SmsConfirmationScreen(viewModel: FinanceViewModel) {
    MaldarDesignTheme {
        SmsConfirmationContent(viewModel)
    }
}

@Composable
private fun SmsConfirmationContent(viewModel: FinanceViewModel) {
    val pending by viewModel.pendingSms.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    var editing by remember { mutableStateOf<PendingSmsEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = MaldarDesign.spacing.lg,
            end = MaldarDesign.spacing.lg,
            top = MaldarDesign.spacing.lg,
            bottom = MaldarDesign.spacing.section
        ),
        verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.md)
    ) {
        item {
            Text(AppStrings.confirmSms, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(MaldarDesign.spacing.xs))
            Text(
                AppStrings.confirmSmsHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pending.isNotEmpty()) {
                Spacer(Modifier.height(MaldarDesign.spacing.sm))
                Text(
                    AppStrings.pendingSms.fa(pending.size),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (pending.isEmpty()) {
            item {
                EmptyState(
                    title = AppStrings.nothingPending,
                    message = AppStrings.confirmSmsEmptyHint,
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaldarDesign.spacing.section)
                )
            }
        }

        items(pending, key = { it.id }) { p ->
            val matchedAccount = p.bankAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
            val warning = when {
                p.parsedAmount == null -> AppStrings.smsAmountNeedsReview
                p.parsedType == null -> AppStrings.smsTypeNeedsReview
                p.bankAccountId == null -> AppStrings.smsAccountNotMatched
                else -> null
            }
            PendingTransactionCard(
                typeLabel = when (p.parsedType) {
                    TxType.EXPENSE -> AppStrings.expense
                    TxType.INCOME -> AppStrings.income
                    TxType.CARD_TO_CARD -> AppStrings.cardToCard
                    null -> AppStrings.unknown
                },
                amount = p.parsedAmount?.let { "${Money.format2(it)} ${AppStrings.moneyUnit}" } ?: AppStrings.amountUnclear,
                sender = "${AppStrings.from} ${p.sender}",
                account = matchedAccount?.let { "${AppStrings.suggestedAccount}: ${it.accountLabel}" },
                date = "${AppStrings.smsDate}: ${JalaliCalendar.formatDate(p.timestampMillis)}",
                balanceAfter = p.parsedBalance?.let { "${AppStrings.balanceAfter}: ${Money.format2(it)} ${AppStrings.moneyUnit}" },
                warningText = warning,
                rawMessage = p.rawMessage,
                confirmLabel = AppStrings.confirmTransaction,
                editLabel = AppStrings.confirmEdit,
                rejectLabel = AppStrings.ignore,
                rawMessageLabel = AppStrings.smsBody,
                expandDescription = AppStrings.expandSmsBody,
                collapseDescription = AppStrings.collapseSmsBody,
                onConfirm = { editing = p },
                onEdit = { editing = p },
                onReject = { viewModel.rejectPendingSms(p) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    editing?.let { p ->
        ConfirmDialog(viewModel = viewModel, pending = p, onDismiss = { editing = null }, onConfirm = { amount, type, category, note, remainder ->
            viewModel.confirmPendingSms(p, amount, type, category, note, remainder)
            editing = null
        })
    }
}

@Composable
private fun ConfirmDialog(
    viewModel: FinanceViewModel,
    pending: PendingSmsEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double, TxType, String, String, Double?) -> Unit
) {
    var amountText by remember { mutableStateOf(pending.parsedAmount?.let(Money::input) ?: "") }
    var type by remember { mutableStateOf(pending.parsedType ?: TxType.EXPENSE) }
    // Default to the first matching category; if none exist (e.g. the categories
    // table is empty) fall back to the generic "سایر" so Save never silently no-ops.
    val fallbackCategory = if (type == TxType.EXPENSE) "سایر" else "سایر"
    var category by remember { mutableStateOf(fallbackCategory) }
    var note by remember { mutableStateOf("") }
    var remainderText by remember { mutableStateOf(pending.parsedBalance?.let(Money::input) ?: "") }
    var showAmountError by remember { mutableStateOf(false) }

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
                    onValueChange = {
                        amountText = it.filter { c -> c.isDigit() || c == '.' }
                        showAmountError = false
                    },
                    label = { Text(AppStrings.amountLabel) },
                    visualTransformation = ThousandsSeparatorTransformation(),
                    isError = showAmountError,
                    supportingText = if (showAmountError) ({ Text(AppStrings.validPositiveAmount) }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmented(
                    options = listOf(AppStrings.expense, AppStrings.income),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelected = { type = if (it == 0) TxType.EXPENSE else TxType.INCOME }
                )
                CategoryPicker(
                    viewModel = viewModel,
                    type = type,
                    selected = category,
                    onSelected = { category = it }
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(AppStrings.noteOptional) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remainderText,
                    onValueChange = { remainderText = sanitizeNumberInput(it) },
                    label = { Text(AppStrings.balanceAfter + " (" + AppStrings.optional + ")") },
                    visualTransformation = ThousandsSeparatorTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    onConfirm(amount, type, category.ifBlank { fallbackCategory }, note, remainderText.toDoubleOrNull())
                } else {
                    showAmountError = true
                }
            }) { Text(AppStrings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.cancel) } }
    )
}
