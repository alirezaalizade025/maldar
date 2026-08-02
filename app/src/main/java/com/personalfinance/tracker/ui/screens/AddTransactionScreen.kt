package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxSource
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme
import com.personalfinance.tracker.ui.design.components.AppButton
import com.personalfinance.tracker.ui.design.components.AppButtonStyle
import com.personalfinance.tracker.ui.design.components.AppCard
import com.personalfinance.tracker.ui.design.components.AppCardStyle
import com.personalfinance.tracker.ui.design.components.MaldarSegmentedControl
import com.personalfinance.tracker.ui.design.components.SectionHeader
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.CrashLogger
import com.personalfinance.tracker.util.Digits
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.util.ThousandsSeparatorTransformation
import com.personalfinance.tracker.util.sanitizeNumberInput
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AddTransactionScreen(
    viewModel: FinanceViewModel,
    accountId: Long? = null,
    smsDate: Long? = null,
    onContinueToList: (() -> Unit)? = null
) {
    MaldarDesignTheme {
        AddTransactionContent(viewModel, accountId, smsDate, onContinueToList)
    }
}

@Composable
private fun AddTransactionContent(
    viewModel: FinanceViewModel,
    accountId: Long?,
    smsDate: Long?,
    onContinueToList: (() -> Unit)?
) {
    val context = LocalContext.current
    val accounts by viewModel.bankAccounts.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val senders by viewModel.smsSenders.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()

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
    var savedCount by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
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

    val activeCategories = when (type) {
        TxType.EXPENSE -> expenseCategories
        TxType.INCOME -> incomeCategories
        TxType.CARD_TO_CARD -> emptyList()
    }
    val effectiveDateMillis = transactionDateMillis ?: System.currentTimeMillis()
    val effectiveTime = remember(effectiveDateMillis) {
        Calendar.getInstance().apply { timeInMillis = effectiveDateMillis }.let {
            Digits.toPersian("%02d:%02d".format(java.util.Locale.US, it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)))
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = MaldarDesign.spacing.lg,
                    end = MaldarDesign.spacing.lg,
                    top = MaldarDesign.spacing.lg,
                    bottom = MaldarDesign.spacing.section
                ),
            verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.lg)
        ) {
        Text(AppStrings.addTransaction, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        MaldarSegmentedControl(
            options = listOf(AppStrings.expense, AppStrings.income, AppStrings.cardToCard),
            selectedIndex = when (type) { TxType.EXPENSE -> 0; TxType.INCOME -> 1; TxType.CARD_TO_CARD -> 2 },
            onSelected = {
                type = when (it) { 0 -> TxType.EXPENSE; 1 -> TxType.INCOME; else -> TxType.CARD_TO_CARD }
                category = "" // let CategoryPicker re-default to the first category of the new type
                if (type == TxType.CARD_TO_CARD) selectedLoanId = null
            }
        )

        AppCard(style = AppCardStyle.HERO, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (rialMode) "${AppStrings.amount} (${AppStrings.rial})" else AppStrings.amountLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(MaldarDesign.spacing.sm))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = sanitizeNumberInput(it)
                        amountError = false
                    },
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    ),
                    placeholder = { Text("۰", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    visualTransformation = ThousandsSeparatorTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError,
                    supportingText = if (amountError) ({ Text(AppStrings.invalidAmount) }) else null,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = if (rialMode) "${AppStrings.amount}، ${AppStrings.rial}" else AppStrings.amountLabel
                    },
                    shape = MaterialTheme.shapes.medium
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AppStrings.unit, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(MaldarDesign.spacing.sm))
                    FilterChip(selected = !rialMode, onClick = { rialMode = false }, label = { Text(AppStrings.toman) })
                    Spacer(Modifier.width(MaldarDesign.spacing.sm))
                    FilterChip(selected = rialMode, onClick = { rialMode = true }, label = { Text(AppStrings.rial) })
                }
            }
        }

        if (sourceSmsBody != null) {
            AppCard(style = AppCardStyle.OUTLINED, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(MaldarDesign.spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(AppStrings.smsBody, style = MaterialTheme.typography.labelLarge)
                        Text(sourceSmsBody.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
            }
        }

        if (type != TxType.CARD_TO_CARD) {
            SectionHeader(AppStrings.category)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)
            ) {
                activeCategories.take(6).forEach { item ->
                    FilterChip(selected = category == item.name, onClick = { category = item.name }, label = { Text(item.name) })
                }
            }
            CategoryPicker(viewModel = viewModel, type = type, selected = category, onSelected = { category = it })
        }

        AppCard(style = AppCardStyle.FLAT, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.md)) {
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

                if (type == TxType.EXPENSE) ExposedDropdownMenuBox(expanded = loanMenuExpanded, onExpandedChange = { loanMenuExpanded = it }) {
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

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)) {
                    OutlinedTextField(
                        value = JalaliCalendar.formatDate(effectiveDateMillis),
                        onValueChange = {}, readOnly = true,
                        label = { Text("تاریخ") },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = effectiveTime,
                        onValueChange = {}, readOnly = true,
                        label = { Text("زمان") },
                        leadingIcon = { Icon(Icons.Filled.AccessTime, contentDescription = null) },
                        modifier = Modifier.widthIn(min = 116.dp).weight(0.65f)
                    )
                }

                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(AppStrings.noteOptional) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                if (selectedAccountId != null) {
                    OutlinedTextField(
                        value = remainderText,
                        onValueChange = { remainderText = sanitizeNumberInput(it) },
                        label = { Text(AppStrings.balanceAfter + " (" + AppStrings.optional + ")") },
                        visualTransformation = ThousandsSeparatorTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        AppStrings.balanceAfter + ": " + (accounts.firstOrNull { it.id == selectedAccountId }?.balance?.let { Money.format2(it) } ?: "—") + " " + AppStrings.moneyUnit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AppButton(
            text = AppStrings.save,
            onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    amountError = false
                    val stored = if (rialMode) amount / 10.0 else amount

                    val remainder = if (remainderText.isNotBlank()) {
                        remainderText.toDoubleOrNull()?.let { if (rialMode) it / 10.0 else it }
                    } else {
                        null
                    }

                    val smsBody = sourceSmsBody
                    val inferredLoanId = selectedLoanId ?: loans.filter { loan ->
                        !loan.isPaid && category.trim() == "وام" &&
                            kotlin.math.abs((if (loan.installment > 0.0) loan.installment else loan.remainingAmount) - stored) < 0.01
                    }.singleOrNull()?.id
                    isSaving = true
                    scope.launch {
                        val result = runCatching {
                            viewModel.addTransaction(
                                amount = stored,
                                type = type,
                                category = if (type == TxType.CARD_TO_CARD) AppStrings.cardToCard else category.ifBlank { "سایر" },
                                note = note,
                                bankAccountId = selectedAccountId,
                                dateMillis = transactionDateMillis ?: System.currentTimeMillis(),
                                loanId = inferredLoanId,
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
                    amountError = true
                    confirmationMessage = AppStrings.invalidAmount
                }
            },
            enabled = !isSaving,
            loading = isSaving,
            modifier = Modifier.fillMaxWidth()
        )

        if (onContinueToList != null) {
            if (savedCount > 0) {
                AppButton(
                    text = AppStrings.continueToList,
                    onClick = onContinueToList,
                    style = AppButtonStyle.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    AppStrings.smsAddHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).semantics { liveRegion = LiveRegionMode.Polite }
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
