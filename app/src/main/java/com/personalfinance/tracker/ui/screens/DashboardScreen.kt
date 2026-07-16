package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: FinanceViewModel, onGoToConfirm: () -> Unit) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val pending by viewModel.pendingSms.collectAsState()

    var monthIncome by remember { mutableStateOf(0.0) }
    var monthExpense by remember { mutableStateOf(0.0) }
    LaunchedEffect(transactions) {
        val (inc, exp) = viewModel.monthlyIncomeExpense(0)
        monthIncome = inc; monthExpense = exp
    }

    val totalBalance = accounts.sumOf { it.balance }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Overview", style = MaterialTheme.typography.headlineMedium)
        }

        if (pending.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onGoToConfirm() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Text("${pending.size} transaction(s) from SMS need confirmation", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Total Balance", color = Color.White.copy(alpha = 0.85f))
                    Text(
                        "₹%,.2f".format(totalBalance),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("This month income", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Text("₹%,.2f".format(monthIncome), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("This month spent", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Text("₹%,.2f".format(monthExpense), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item { Text("Recent Transactions", style = MaterialTheme.typography.titleLarge) }

        if (transactions.isEmpty()) {
            item { Text("No transactions yet. Add one from the Add tab.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(transactions.take(15)) { tx ->
            val df = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(tx.category, fontWeight = FontWeight.Medium)
                        Text(df.format(Date(tx.dateMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        if (tx.note.isNotBlank()) {
                            Text(tx.note, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        (if (tx.type == TxType.INCOME) "+ " else "- ") + "₹%,.2f".format(tx.amount),
                        color = if (tx.type == TxType.INCOME) Color(0xFF1B7A5A) else Color(0xFFE8604C),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}
