package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.BankAccountEntity
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.SmsInboxReader
import com.personalfinance.tracker.viewmodel.FinanceViewModel

@Composable
fun AccountSmsScreen(
    account: BankAccountEntity,
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onSelectSms: (smsDateMillis: Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val senders by viewModel.smsSenders.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    val accountSenders = remember(senders, account.id) {
        senders.filter { it.bankAccountId == account.id }.map { it.senderId }
    }

    var smsList by remember { mutableStateOf<List<SmsInboxReader.SmsMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Reload the inbox whenever the account's senders change.
    LaunchedEffect(accountSenders) {
        loading = true
        smsList = SmsInboxReader.allSmsForSenders(context, accountSenders)
        loading = false
    }

    // Reconciliation lookup per SMS (amount + same day against existing transactions).
    var reconciled by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    LaunchedEffect(smsList, transactions, account.id) {
        val map = mutableMapOf<Long, Boolean>()
        smsList.forEach { sms ->
            map[sms.dateMillis] = if (sms.amount != null) {
                viewModel.isReconciled(account.id, sms.amount, sms.dateMillis)
            } else false
        }
        reconciled = map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.smsList + " • " + account.accountLabel) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.back) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (accountSenders.isEmpty()) {
                Text(AppStrings.noSendersForAccount, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                return@Column
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }
            if (smsList.isEmpty()) {
                Text(AppStrings.noSmsForAccount, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                return@Column
            }

            Text(AppStrings.selectSmsToAdd, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(smsList) { sms ->
                    val isRecon = reconciled[sms.dateMillis] ?: false
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().clickable { onSelectSms(sms.dateMillis) }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Message, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sms.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(JalaliCalendar.formatDateTime(sms.dateMillis), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    sms.amount?.let {
                                        Text(Money.format(it) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold,
                                            color = if (sms.type?.name == "INCOME") androidx.compose.ui.graphics.Color(0xFF1B7A5A)
                                            else androidx.compose.ui.graphics.Color(0xFFE8604C))
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Checkbox(checked = isRecon, onCheckedChange = null, enabled = false)
                                Text(
                                    if (isRecon) AppStrings.reconciled else AppStrings.notReconciled,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRecon) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
