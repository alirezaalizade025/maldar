package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personalfinance.tracker.data.*
import com.personalfinance.tracker.util.*
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.util.Calendar

@Composable
internal fun TransactionSearchDialog(viewModel: FinanceViewModel, initialAccountId: Long? = null, onDismiss: () -> Unit) {
    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    var query by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(initialAccountId) }
    var category by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf<TxType?>(null) }
    var from by remember { mutableStateOf<Long?>(null) }
    var to by remember { mutableStateOf<Long?>(null) }
    var minimum by remember { mutableStateOf("") }
    var maximum by remember { mutableStateOf("") }
    var filtersVisible by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val results = remember(transactions, accounts, query, accountId, category, type, from, to, minimum, maximum) {
        searchTransactions(transactions, accounts, TransactionFilter(query, accountId, category, type, from, to,
            minimum.toDoubleOrNull(), maximum.toDoubleOrNull()))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().imePadding().safeDrawingPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("جست‌وجوی تراکنش‌ها", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onDismiss) { Text("بستن") }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("شرح، دسته، مبلغ، حساب یا متن پیامک") }, singleLine = true)
                    Row {
                        TextButton(onClick = { filtersVisible = !filtersVisible }) { Text("فیلترها") }
                        TextButton(onClick = { query = ""; accountId = null; category = null; type = null; from = null; to = null; minimum = ""; maximum = "" }) { Text("پاک کردن فیلترها") }
                    }
                }
                if (filtersVisible) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchChoice("حساب", accounts.firstOrNull { it.id == accountId }?.accountLabel ?: "همه", listOf(null to "همه") + accounts.map { it.id to it.accountLabel }) { accountId = it }
                        SearchChoice("نوع", type?.let(::transactionTypeLabel) ?: "همه", listOf(null to "همه") + TxType.entries.map { it to transactionTypeLabel(it) }) { type = it }
                        SearchChoice("دسته", category ?: "همه", listOf(null to "همه") + transactions.map { it.category }.distinct().sorted().map { it to it }) { category = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(minimum, { minimum = sanitizeNumberInput(it) }, Modifier.weight(1f), label = { Text("حداقل مبلغ") }, singleLine = true)
                            OutlinedTextField(maximum, { maximum = sanitizeNumberInput(it) }, Modifier.weight(1f), label = { Text("حداکثر مبلغ") }, singleLine = true)
                        }
                        OutlinedButton(onClick = { showTransactionDatePicker(context, from ?: System.currentTimeMillis()) { from = dayBoundary(it, false) } }) { Text("از تاریخ: " + (from?.let(JalaliCalendar::formatDate) ?: "همه")) }
                        OutlinedButton(onClick = { showTransactionDatePicker(context, to ?: System.currentTimeMillis()) { to = dayBoundary(it, true) } }) { Text("تا تاریخ: " + (to?.let(JalaliCalendar::formatDate) ?: "همه")) }
                        if ((from != null && to != null && from!! > to!!) || (minimum.toDoubleOrNull() ?: 0.0) > (maximum.toDoubleOrNull() ?: Double.MAX_VALUE)) {
                            Text("بازهٔ فیلتر معتبر نیست.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item { Text(Digits.toPersian("${results.size} تراکنش")) }
                if (results.isEmpty()) item { Text("تراکنشی پیدا نشد.") }
                items(results, key = { it.id }) { tx ->
                    OutlinedCard(Modifier.fillMaxWidth().clickable { selectedId = tx.id }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${tx.category} • ${transactionTypeLabel(tx.type)} • ${Money.format2(tx.amount)} تومان")
                            Text(JalaliCalendar.formatDateTime(tx.dateMillis), style = MaterialTheme.typography.bodySmall)
                            if (tx.note.isNotBlank()) Text(tx.note)
                            if (tx.parentTransactionId != null) Text("بازپرداخت هزینهٔ مشترک", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    transactions.firstOrNull { it.id == selectedId }?.let { tx ->
        key(tx.id) { EditTransactionDialog(tx, accounts, viewModel) { selectedId = null } }
    }
}

internal fun transactionTypeLabel(type: TxType) = when (type) {
    TxType.INCOME -> "واریز"; TxType.EXPENSE -> "هزینه"; TxType.CARD_TO_CARD -> "کارت به کارت"
}

private fun dayBoundary(millis: Long, end: Boolean): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    if (end) { add(Calendar.DAY_OF_MONTH, 1); timeInMillis -= 1 }
}.timeInMillis

@Composable
private fun <T> SearchChoice(label: String, value: String, choices: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $value") }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelected(id); expanded = false }) }
        }
    }
}
