package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.*
import com.personalfinance.tracker.sms.SmsParser
import com.personalfinance.tracker.util.*
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
internal fun BalanceCheckDialog(accountId: Long, viewModel: FinanceViewModel, onSearch: () -> Unit, onDismiss: () -> Unit) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val senders by viewModel.smsSenders.collectAsState()
    val pending by viewModel.pendingSms.collectAsState()
    val reviewed by viewModel.reviewedSms.collectAsState()
    val account = accounts.firstOrNull { it.id == accountId }
    val context = LocalContext.current
    var inbox by remember { mutableStateOf<SmsInboxReader.Result?>(null) }
    var loading by remember { mutableStateOf(true) }
    var manual by remember { mutableStateOf(false) }
    var bankText by remember { mutableStateOf("") }
    val senderIds = senders.filter { it.bankAccountId == accountId }.map { it.senderId }
    LaunchedEffect(accountId, senderIds, account?.accountLast4) {
        loading = true
        try {
            inbox = withContext(Dispatchers.IO) { SmsInboxReader.lastSmsForSenders(context, senderIds, account?.accountLast4.orEmpty(), Settings.ignoredSms) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { inbox = null }
        finally { loading = false }
    }
    if (account == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    // Transaction dates are editable; bank checks use the original SMS time only.
    val storedSnapshots = transactions.filter { it.bankAccountId == accountId && it.source == TxSource.SMS && it.smsTimestampMillis != null && it.rawSms != null }
        .mapNotNull { tx -> SmsParser.parse(tx.rawSms!!).balanceAfter?.let { SmsInboxReader.Result(tx.rawSms, it, tx.smsTimestampMillis) } }
    val pendingSnapshots = (pending + reviewed).filter { it.bankAccountId == accountId &&
        SmsInboxReader.smsKey(it.sender, it.timestampMillis) !in Settings.ignoredSms }
        .mapNotNull { sms -> SmsParser.parse(sms.rawMessage).balanceAfter?.let { SmsInboxReader.Result(sms.rawMessage, it, sms.timestampMillis) } }
    val now = System.currentTimeMillis()
    val snapshot = (storedSnapshots + pendingSnapshots + listOfNotNull(inbox))
        .filter { it.amount != null && it.timestampMillis != null && it.timestampMillis <= now }
        .maxByOrNull { it.timestampMillis!! }
    val bankTransactions = transactions.map { tx ->
        if (tx.source != TxSource.SMS || tx.smsTimestampMillis != null) tx else {
            val times = (pending + reviewed).filter { it.rawMessage == tx.rawSms && it.bankAccountId == tx.bankAccountId }
                .map { it.timestampMillis }.distinct()
            val knownTime = times.singleOrNull()
            tx.copy(smsTimestampMillis = knownTime)
        }
    }
    val unknownSmsTime = bankTransactions.any { it.source == TxSource.SMS && it.smsTimestampMillis == null &&
        (it.bankAccountId == accountId || it.toAccountId == accountId) }
    val bankBalance = if (manual) bankText.toDoubleOrNull()?.takeIf { it.isFinite() } else snapshot?.amount
    val result = bankBalance?.takeIf { manual || !unknownSmsTime }?.let { checkAccountBalance(account, it, if (manual) now else snapshot!!.timestampMillis!!, bankTransactions, now) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("بررسی موجودی • ${account.accountLabel}") }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("موجودی برنامه: ${Money.format2(account.balance)} تومان")
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row {
                Checkbox(manual, { manual = it })
                Text("ورود دستی موجودی فعلی بانک")
            }
            if (manual) {
                OutlinedTextField(bankText, { bankText = Digits.toEnglish(it).filter { c -> c.isDigit() || c == '.' || c == '-' } }, label = { Text("موجودی فعلی بانک (تومان)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else if (snapshot != null) {
                Text("ماندهٔ پیامک: ${Money.format2(snapshot.amount!!)} تومان")
                Text("زمان پیامک: ${JalaliCalendar.formatDateTime(snapshot.timestampMillis!!)}")
                snapshot.body?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                result?.let { Text("خالص جابه‌جایی‌های ثبت‌شده پس از پیامک: ${Money.format2(it.movementsAfter)} تومان") }
            } else if (!loading) Text("پیامک دارای مانده پیدا نشد یا دسترسی پیامک موجود نیست. موجودی فعلی بانک را دستی وارد کنید.")
            if (!manual && unknownSmsTime && !loading) Text("زمان اصلی برخی تراکنش‌های پیامکی قدیمی مشخص نیست؛ برای مقایسهٔ دقیق، موجودی فعلی بانک را دستی وارد کنید.", color = MaterialTheme.colorScheme.error)
            result?.let {
                Text("موجودی قابل مقایسهٔ بانک: ${Money.format2(it.expectedBalance)} تومان")
                Text(if (abs(it.difference) < 0.01) "موجودی‌ها برابر هستند." else "اختلاف برنامه با بانک: ${Money.format2(it.difference)} تومان",
                    color = if (abs(it.difference) < 0.01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                if (abs(it.difference) >= 0.01) Text(if (it.difference > 0) "موجودی برنامه بیشتر است؛ واریز تکراری یا هزینهٔ ثبت‌نشده را بررسی کنید." else "موجودی برنامه کمتر است؛ هزینهٔ تکراری یا واریز ثبت‌نشده را بررسی کنید.")
            }
            Text("این بررسی موجودی را تغییر نمی‌دهد. برای پیامک قدیمی، فقط جابه‌جایی‌های ثبت‌شده پس از آن لحاظ می‌شود؛ تراکنش ثبت‌نشده همچنان نیاز به بررسی دارد.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onSearch) { Text("جست‌وجوی تراکنش‌های این حساب") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } })
}
