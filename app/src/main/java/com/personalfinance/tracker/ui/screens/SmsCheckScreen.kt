package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.ui.theme.AppCard
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.Settings
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.Settings
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.viewmodel.FinanceViewModel

/**
 * "Unchecked SMS" review: shows bank inbox SMS received after the last check
 * (Settings.lastSmsCheckMillis). If never checked, the list is empty. Each item
 * can be rejected (dismissed) or confirmed (opens Add Transaction prefilled).
 * Finishing the review stamps the check time so future runs only show new ones.
 */
@Composable
fun SmsCheckScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onConfirmSms: (accountId: Long?, smsDateMillis: Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val senders by viewModel.smsSenders.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()

    val since = Settings.lastSmsCheckMillis
    val senderIds = remember(senders) { senders.map { it.senderId } }

    var list by remember(senderIds, since) {
        mutableStateOf(SmsInboxReader.uncheckedSince(context, senderIds, since))
    }
    var rejected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val visible = list.filter { !rejected.contains(it.dateMillis) }

    // Map sender -> accountId for prefilling confirm.
    fun accountFor(address: String): Long? =
        senders.firstOrNull { s -> address.contains(s.senderId, ignoreCase = true) || s.senderId.equals(address, ignoreCase = true) }?.bankAccountId

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(AppStrings.checkSms) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.back) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (since <= 0L) {
                Text(AppStrings.noSmsCheckedYet, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else if (visible.isEmpty()) {
                Text(AppStrings.noUncheckedSms, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visible) { sms ->
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(sms.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(JalaliCalendar.formatDateTime(sms.dateMillis), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    sms.amount?.let {
                                        Text(Money.format2(it) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold,
                                            color = if (sms.type?.name == "INCOME") androidx.compose.ui.graphics.Color(0xFF1B7A5A)
                                            else androidx.compose.ui.graphics.Color(0xFFE8604C))
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(modifier = Modifier.weight(1f),
                                        onClick = { onConfirmSms(accountFor(sms.address), sms.dateMillis) }) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(AppStrings.confirm)
                                    }
                                    OutlinedButton(modifier = Modifier.weight(1f),
                                        onClick = { rejected = rejected + sms.dateMillis }) {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(AppStrings.reject)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    Settings.lastSmsCheckMillis = System.currentTimeMillis()
                    onBack()
                }
            ) { Text(AppStrings.finishSmsCheck) }
        }
    }
}
