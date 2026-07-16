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
import com.personalfinance.tracker.ui.theme.Coral
import com.personalfinance.tracker.ui.theme.Emerald
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.util.Date

@Composable
fun DashboardScreen(viewModel: FinanceViewModel, onGoToConfirm: () -> Unit) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val pending by viewModel.pendingSms.collectAsState()

    var monthIncome by remember { mutableStateOf(0.0) }
    var monthExpense by remember { mutableStateOf(0.0) }
    var trend by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    LaunchedEffect(transactions) {
        val (inc, exp) = viewModel.monthlyIncomeExpense(0)
        monthIncome = inc; monthExpense = exp
        trend = viewModel.monthlyHistory(6)
    }

    val totalBalance = accounts.sumOf { it.balance }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(AppStrings.overview, style = MaterialTheme.typography.headlineMedium)
        }

        // First-launch onboarding: guide the user when the app is completely empty.
        if (accounts.isEmpty() && transactions.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(AppStrings.onboardingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(AppStrings.onboardingBody, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(AppStrings.monthlyTrend, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    if (trend.isEmpty() || trend.all { it.first == 0.0 && it.second == 0.0 }) {
                        Text(AppStrings.noTrendData, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    } else {
                        MonthTrendGraph(data = trend)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(Emerald, RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(AppStrings.reportIncome, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(Coral, RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(AppStrings.reportExpense, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(Color(0xFF5A5F66), RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(AppStrings.net, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
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
                        Text(AppStrings.pendingSms.format(pending.size), fontWeight = FontWeight.Medium)
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
                    Text(AppStrings.totalBalance, color = Color.White.copy(alpha = 0.85f))
                    Text(
                        Money.format2(totalBalance) + " " + AppStrings.moneyUnit,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(AppStrings.monthIncome, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Text(Money.format2(monthIncome) + " " + AppStrings.moneyUnit, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text(AppStrings.monthExpense, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Text(Money.format2(monthExpense) + " " + AppStrings.moneyUnit, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item { Text(AppStrings.recentTransactions, style = MaterialTheme.typography.titleLarge) }

        if (transactions.isEmpty()) {
            item { Text(AppStrings.noTransactions, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }

        items(transactions.take(15)) { tx ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                        viewModel.deleteTransaction(tx)
                        true
                    } else false
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromEndToStart = true,
                enableDismissFromStartToEnd = true,
                backgroundContent = {
                    val direction = dismissState.dismissDirection
                    val color = if (direction != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surface
                    Box(
                        Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        Text(AppStrings.delete, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(tx.category, fontWeight = FontWeight.Medium)
                            Text(JalaliCalendar.formatDateTime(tx.dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            if (tx.note.isNotBlank()) {
                                Text(tx.note, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            (if (tx.type == TxType.INCOME) "+ " else "- ") + Money.format2(tx.amount) + " " + AppStrings.moneyUnit,
                            color = if (tx.type == TxType.INCOME) Color(0xFF1B7A5A) else Color(0xFFE8604C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}
