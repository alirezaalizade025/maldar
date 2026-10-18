package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.*
import com.personalfinance.tracker.util.*
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ShareDescription(tx: TransactionEntity, transactions: List<TransactionEntity>) {
    val children = transactions.filter { it.parentTransactionId == tx.id }.sortedBy { it.dateMillis }
    if (children.isNotEmpty()) {
        Text("مبلغ اصلی: ${Money.format2(tx.amount)} تومان")
        children.forEach { child ->
            Text("بازپرداخت: ${Money.format2(child.amount)} تومان • ${JalaliCalendar.formatDateTime(child.dateMillis)}" + child.note.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
        val current = reportingTransactions(transactions).firstOrNull { it.id == tx.id }?.amount ?: tx.amount
        Text("هزینهٔ خالص تا امروز: ${Money.format2(current)} تومان", color = MaterialTheme.colorScheme.primary)
    }
    tx.parentTransactionId?.let { parentId ->
        transactions.firstOrNull { it.id == parentId }?.let { parent ->
            Text("بازپرداختِ ${parent.category} • ${JalaliCalendar.formatDate(parent.dateMillis)}", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun ShareTransactionDialog(transactionId: Long, viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    val tx = transactions.firstOrNull { it.id == transactionId }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun link(childId: Long, parentId: Long?) {
        busy = true
        scope.launch {
            try { viewModel.setShareParent(childId, parentId); error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "ثبت ارتباط ناموفق بود." }
            finally { busy = false }
        }
    }
    if (tx == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val children = transactions.filter { it.parentTransactionId == tx.id }
    val remaining = shareRemainingAmount(tx, transactions)
    val candidates = if (tx.type == TxType.EXPENSE) {
        transactions.filter { it.type == TxType.INCOME && it.parentTransactionId == null && it.amount <= remaining }
    } else {
        transactions.filter { parent -> parent.type == TxType.EXPENSE && parent.loanId == null &&
            shareRemainingAmount(parent, transactions) >= tx.amount }
    }
    val results = searchTransactions(candidates, accounts, TransactionFilter(query = query))
    AlertDialog(onDismissRequest = onDismiss, title = { Text("هزینهٔ مشترک و بازپرداخت‌ها") }, text = {
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("مبلغ اصلی و هر بازپرداخت در شرح نمایش داده می‌شود. بازپرداخت متصل در درآمد گزارش نمی‌شود.")
                ShareDescription(tx, transactions)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            items(children, key = { "child-${it.id}" }) { child ->
                OutlinedButton(onClick = { link(child.id, null) }, enabled = !busy) { Text("قطع ارتباط ${Money.format2(child.amount)} تومان • ${child.note.ifBlank { child.category }}") }
            }
            if (tx.parentTransactionId != null) item {
                OutlinedButton(onClick = { link(tx.id, null) }, enabled = !busy) { Text("قطع ارتباط با هزینهٔ اصلی") }
            }
            if (tx.parentTransactionId == null) {
                item {
                    Text(if (tx.type == TxType.EXPENSE) "انتخاب واریزِ بازپرداخت" else "انتخاب هزینهٔ اصلی")
                    OutlinedTextField(query, { query = it }, label = { Text("جست‌وجو") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                if (results.isEmpty()) item { Text("تراکنش واجد شرایط پیدا نشد. بازپرداخت باید واریز ثبت‌شده باشد و از ماندهٔ هزینه بیشتر نباشد.") }
                items(results, key = { "candidate-${it.id}" }) { candidate ->
                    OutlinedButton(onClick = {
                        if (tx.type == TxType.EXPENSE) link(candidate.id, tx.id) else link(tx.id, candidate.id)
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("${Money.format2(candidate.amount)} تومان • ${candidate.note.ifBlank { candidate.category }} • ${JalaliCalendar.formatDate(candidate.dateMillis)}")
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } })
}
