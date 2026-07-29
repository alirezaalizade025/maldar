package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.theme.AppCard
import com.personalfinance.tracker.data.CategoryTotal
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.viewmodel.FinanceViewModel
import java.util.Calendar

private val chartColors = listOf(
    Color(0xFF1B7A5A), Color(0xFFE8604C), Color(0xFF3E7CB1),
    Color(0xFFE0A930), Color(0xFF8E5FB0), Color(0xFF5A5F66), Color(0xFF2B9D8F)
)

@Composable
fun ReportsScreen(viewModel: FinanceViewModel) {
    var monthOffset by remember { mutableStateOf(0) }
    var income by remember { mutableStateOf(0.0) }
    var expense by remember { mutableStateOf(0.0) }
    var breakdown by remember { mutableStateOf<List<CategoryTotal>>(emptyList()) }
    // Charts show the spent amount by default; toggle to view as a percentage.
    var byPercent by remember { mutableStateOf(false) }
    var trend by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var balanceTrend by remember { mutableStateOf<List<Double>>(emptyList()) }

    LaunchedEffect(monthOffset) {
        runCatching {
            val (inc, exp) = viewModel.monthlyIncomeExpense(monthOffset)
            income = inc; expense = exp
            breakdown = viewModel.categoryBreakdown(monthOffset)
            trend = viewModel.monthlyHistory(6)
            balanceTrend = viewModel.balanceHistory(6)
        }
    }

    val monthLabel = remember(monthOffset) {
        JalaliCalendar.monthLabel(Calendar.getInstance(), monthOffset)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text(AppStrings.reports, style = MaterialTheme.typography.headlineMedium) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { monthOffset-- }) { Text(AppStrings.prev) }
                Text(monthLabel, style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { monthOffset++ }, enabled = monthOffset < 12) { Text(AppStrings.next) }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(AppStrings.monthlyTrend, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    if (trend.isEmpty() || trend.all { it.first == 0.0 && it.second == 0.0 }) {
                        Text(AppStrings.noTrendData, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    } else {
                        MonthTrendGraph(data = trend, balanceLine = balanceTrend)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ChartLegend(AppStrings.reportIncome, Color(0xFF1B7A5A))
                            ChartLegend(AppStrings.reportExpense, Color(0xFFE8604C))
                            ChartLegend(AppStrings.net, Color(0xFF5A5F66))
                            ChartLegend(AppStrings.balanceTrend, Color(0xFF2B6CB0))
                        }
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SummaryValue(AppStrings.reportIncome, income, Color(0xFF1B7A5A))
                    SummaryValue(AppStrings.reportExpense, expense, Color(0xFFE8604C))
                    SummaryValue(AppStrings.net, income - expense, MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.spendingByCategory, style = MaterialTheme.typography.titleLarge)
                FilterChip(
                    selected = byPercent,
                    onClick = { byPercent = !byPercent },
                    label = { Text(if (byPercent) AppStrings.viewByAmount else AppStrings.viewByPercent) }
                )
            }
        }

        if (breakdown.isEmpty()) {
            item { Text(AppStrings.noExpenses, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        } else {
            val maxVal = breakdown.maxOf { it.total }
            val total = breakdown.sumOf { it.total }.coerceAtLeast(1.0)
            item {
                CategoryDonutChart(breakdown, total)
            }
            itemsIndexed(breakdown, key = { index, item -> "${item.category}-$index" }) { index, category ->
                CategoryBreakdownRow(
                    item = category,
                    color = chartColors[index % chartColors.size],
                    total = total,
                    maxValue = maxVal,
                    byPercent = byPercent
                )
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SummaryValue(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text("${Money.format(amount)} ${AppStrings.moneyUnit}", fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun CategoryDonutChart(breakdown: List<CategoryTotal>, total: Double) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(190.dp)) {
                var startAngle = -90f
                breakdown.forEachIndexed { index, item ->
                    val sweep = (item.total / total * 360.0).toFloat()
                    drawArc(
                        color = chartColors[index % chartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 28.dp.toPx())
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("۱۰۰٪", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(AppStrings.totalExpenses, style = MaterialTheme.typography.labelSmall)
                Text("${Money.format(total)} ${AppStrings.moneyUnit}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    item: CategoryTotal,
    color: Color,
    total: Double,
    maxValue: Double,
    byPercent: Boolean
) {
    val exactFraction = (item.total / total).toFloat().coerceIn(0f, 1f)
    val barFraction = if (byPercent) exactFraction else (item.total / maxValue).toFloat().coerceIn(0f, 1f)
    val percent = item.total / total * 100.0
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(7.dp))
                    Text(item.category, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (byPercent) "٪${Money.format(percent)}"
                    else "${Money.format(item.total)} ${AppStrings.moneyUnit} (٪${Money.format(percent)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(7.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(9.dp)) {
                drawRoundRect(color.copy(alpha = 0.18f), size = Size(size.width, size.height))
                drawRoundRect(color, size = Size(size.width * barFraction, size.height))
            }
        }
    }
}
