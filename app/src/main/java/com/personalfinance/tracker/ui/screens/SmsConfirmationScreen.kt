package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.personalfinance.tracker.util.fa
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
        SmsTransactionDialog(pending = p, accounts = accounts, viewModel = viewModel, onDismiss = { editing = null })
    }
}
