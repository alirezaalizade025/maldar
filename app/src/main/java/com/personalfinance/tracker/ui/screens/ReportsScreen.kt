package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.CategoryTotal
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme
import com.personalfinance.tracker.ui.design.components.AmountTone
import com.personalfinance.tracker.ui.design.components.AppCard
import com.personalfinance.tracker.ui.design.components.AppCardStyle
import com.personalfinance.tracker.ui.design.components.EmptyState
import com.personalfinance.tracker.ui.design.components.MaldarSegmentedControl
import com.personalfinance.tracker.ui.design.components.MetricCard
import com.personalfinance.tracker.ui.design.components.SectionHeader
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
    MaldarDesignTheme {
        ReportsContent(viewModel)
    }
}

@Composable
private fun ReportsContent(viewModel: FinanceViewModel) {
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    var monthOffset by remember { mutableIntStateOf(0) }
    var accountFilter by remember { mutableStateOf<Long?>(null) }
    var showMonthlyChart by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val monthTransactions = transactions.filter { tx ->
        JalaliCalendar.isInJalaliMonth(tx.dateMillis, monthOffset) &&
            (accountFilter == null || tx.bankAccountId == accountFilter)
    }
    val income = monthTransactions.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = monthTransactions.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val transfers = monthTransactions.filter { it.type == TxType.CARD_TO_CARD }.sumOf { it.amount }
    val breakdown = monthTransactions.filter { it.type == TxType.EXPENSE }
        .groupingBy { it.category }.fold(0.0) { total, tx -> total + tx.amount }
        .map { CategoryTotal(it.key, it.value) }.sortedByDescending { it.total }
    val trend = (5 downTo 0).map { back ->
        val offset = -back
        val monthTransactionsForTrend = transactions.filter { tx ->
            JalaliCalendar.isInJalaliMonth(tx.dateMillis, offset) &&
                (accountFilter == null || tx.bankAccountId == accountFilter)
        }
        monthTransactionsForTrend.filter { it.type == TxType.INCOME }.sumOf { it.amount } to
            monthTransactionsForTrend.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    }

    val monthLabel = remember(monthOffset) {
        JalaliCalendar.monthLabel(Calendar.getInstance(), monthOffset)
    }

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
            Text(AppStrings.reports, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                AppStrings.monthlyFinancialReport,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            AppCard(style = AppCardStyle.OUTLINED, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { monthOffset-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.prev)
                    }
                    Text(monthLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { monthOffset++ }, enabled = monthOffset < 12) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = AppStrings.next)
                    }
                }
            }
        }

        item {
            SectionHeader(AppStrings.reportAccount)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = accountFilter == null,
                    onClick = { accountFilter = null },
                    label = { Text(AppStrings.allAccounts) }
                )
                accounts.forEach { account ->
                    FilterChip(
                        selected = accountFilter == account.id,
                        onClick = { accountFilter = if (accountFilter == account.id) null else account.id },
                        label = { Text(account.accountLabel) }
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)) {
                MetricCard(
                    AppStrings.reportIncome,
                    Money.format(income),
                    Modifier.weight(1f),
                    tone = AmountTone.POSITIVE
                )
                MetricCard(
                    AppStrings.reportExpense,
                    Money.format(expense),
                    Modifier.weight(1f),
                    tone = AmountTone.NEGATIVE
                )
            }
        }

        item {
            MetricCard(AppStrings.net, Money.format(income - expense), Modifier.fillMaxWidth())
        }

        if (transfers > 0.0) {
            item {
                MetricCard(AppStrings.cardToCard, Money.format(transfers), Modifier.fillMaxWidth())
            }
        }

        item {
            SectionHeader(if (showMonthlyChart) AppStrings.monthlyTrend else AppStrings.dailyReport)
            MaldarSegmentedControl(
                options = listOf(AppStrings.dailyReport, AppStrings.monthlyTrend),
                selectedIndex = if (showMonthlyChart) 1 else 0,
                onSelected = { showMonthlyChart = it == 1 }
            )
        }

        item {
            AppCard(style = AppCardStyle.RAISED, modifier = Modifier.fillMaxWidth()) {
                if (showMonthlyChart) {
                    if (trend.isEmpty() || trend.all { it.first == 0.0 && it.second == 0.0 }) {
                        EmptyState(AppStrings.monthlyTrend, AppStrings.noTrendData)
                    } else {
                        MonthTrendGraph(data = trend)
                    }
                } else {
                    DayTrendGraph(transactions = monthTransactions)
                }
            }
        }

        item {
            SectionHeader(AppStrings.monthlyTransactions)
        }

        if (monthTransactions.isNotEmpty()) {
            item {
                AppCard(style = AppCardStyle.OUTLINED, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        monthTransactions.sortedByDescending { it.dateMillis }.take(8).forEach { tx ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = tx.category.ifBlank { AppStrings.unknown },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = tx.note.ifBlank { JalaliCalendar.formatDate(tx.dateMillis) },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (tx.type == TxType.CARD_TO_CARD) AppStrings.cardToCard else if (tx.type == TxType.INCOME) AppStrings.income else AppStrings.expense,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (tx.type == TxType.EXPENSE) MaldarDesign.colors.negative else MaldarDesign.colors.positive
                                    )
                                    Text(
                                        text = "${Money.format(tx.amount)} ${AppStrings.moneyUnit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.type == TxType.EXPENSE) MaldarDesign.colors.negative else MaldarDesign.colors.positive
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(AppStrings.spendingByCategory)
        }

        if (breakdown.isEmpty()) {
            item {
                EmptyState(
                    title = AppStrings.spendingByCategory,
                    message = AppStrings.noExpenses,
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaldarDesign.spacing.lg)
                )
            }
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
                    expanded = selectedCategory == category.category,
                    transactions = monthTransactions.filter {
                        it.type == TxType.EXPENSE && it.category == category.category
                    },
                    onClick = {
                        selectedCategory = if (selectedCategory == category.category) null else category.category
                    }
                )
            }
        }

    }
}

@Composable
private fun DayTrendGraph(transactions: List<com.personalfinance.tracker.data.TransactionEntity>) {
    val daily = transactions.groupBy {
        JalaliCalendar.fromGregorian(Calendar.getInstance().apply { timeInMillis = it.dateMillis }).day
    }
    val income = (1..31).map { day -> daily[day].orEmpty().filter { it.type == TxType.INCOME }.sumOf { it.amount } }
    val expense = (1..31).map { day -> daily[day].orEmpty().filter { it.type == TxType.EXPENSE }.sumOf { it.amount } }
    val maxValue = (income + expense).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    if (income.all { it == 0.0 } && expense.all { it == 0.0 }) {
        EmptyState(AppStrings.dailyReport, AppStrings.noTrendData)
        return
    }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val incomeColor = MaldarDesign.colors.positive
    val expenseColor = MaldarDesign.colors.negative
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val chartDescription = "${AppStrings.dailyReport}، ${AppStrings.reportIncome}: ${Money.format(income.sum())} ${AppStrings.moneyUnit}، ${AppStrings.reportExpense}: ${Money.format(expense.sum())} ${AppStrings.moneyUnit}"
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChartSummaryChip(AppStrings.reportIncome, income.sum(), incomeColor)
            ChartSummaryChip(AppStrings.reportExpense, expense.sum(), expenseColor)
        }
        Canvas(
            Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = chartDescription }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val index = ((offset.x / size.width) * 31).toInt().coerceIn(0, 30)
                        selectedDay = index
                    }
                }
        ) {
            val groupWidth = size.width / 31f
            val baseY = size.height - 24.dp.toPx()
            val chartHeight = baseY - 8.dp.toPx()
            val gridSteps = 4
            repeat(gridSteps) { step ->
                val y = baseY - (chartHeight / (gridSteps - 1)) * step
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            income.forEachIndexed { index, value ->
                val x = index * groupWidth
                val normalized = (value / maxValue * chartHeight).toFloat()
                drawRect(incomeColor, Offset(x + groupWidth * 0.08f, baseY - normalized), Size(groupWidth * 0.38f, normalized))
                val expenseValue = expense[index]
                val normalizedExpense = (expenseValue / maxValue * chartHeight).toFloat()
                drawRect(expenseColor, Offset(x + groupWidth * 0.52f, baseY - normalizedExpense), Size(groupWidth * 0.38f, normalizedExpense))
            }
        }
        selectedDay?.let { dayIndex ->
            val dayIncome = income[dayIndex]
            val dayExpense = expense[dayIndex]
            val dayLabel = (dayIndex + 1).toString()
            Text(
                text = "${AppStrings.day} $dayLabel • ${AppStrings.reportIncome}: ${Money.format(dayIncome)} • ${AppStrings.reportExpense}: ${Money.format(dayExpense)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChartLegend(AppStrings.reportIncome, incomeColor)
            ChartLegend(AppStrings.reportExpense, expenseColor)
        }
    }
}

@Composable
private fun ChartSummaryChip(label: String, amount: Double, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
        Text(
            text = "$label: ${Money.format(amount)} ${AppStrings.moneyUnit}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
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
private fun CategoryDonutChart(breakdown: List<CategoryTotal>, total: Double) {
    val description = breakdown.joinToString("، ") {
        "${it.category}: ${Money.format(it.total / total * 100.0)} درصد"
    }
    AppCard(modifier = Modifier.fillMaxWidth(), style = AppCardStyle.RAISED) {
        Row(
            Modifier.fillMaxWidth().semantics {
                contentDescription = "${AppStrings.totalExpenses}: ${Money.format(total)} ${AppStrings.moneyUnit}، $description"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.md)
        ) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    breakdown.forEachIndexed { index, item ->
                        val sweep = (item.total / total * 360.0).toFloat()
                        drawArc(chartColors[index % chartColors.size], startAngle, sweep, false, style = Stroke(24.dp.toPx()))
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("۱۰۰٪", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(AppStrings.totalExpenses, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                breakdown.forEachIndexed { index, item ->
                    ChartLegend(
                        "${item.category}  ٪${Money.format(item.total / total * 100.0)}",
                        chartColors[index % chartColors.size]
                    )
                }
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
    expanded: Boolean,
    transactions: List<com.personalfinance.tracker.data.TransactionEntity>,
    onClick: () -> Unit
) {
    val barFraction = (item.total / maxValue).toFloat().coerceIn(0f, 1f)
    val percent = item.total / total * 100.0
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), style = AppCardStyle.OUTLINED) {
        Column(Modifier.semantics {
            contentDescription = "${item.category}، ${Money.format(item.total)} ${AppStrings.moneyUnit}، ${Money.format(percent)} درصد"
        }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(7.dp))
                    Text(item.category, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${Money.format(item.total)} ${AppStrings.moneyUnit} (٪${Money.format(percent)})",
                    modifier = Modifier.padding(start = MaldarDesign.spacing.sm),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(7.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(9.dp)) {
                drawRoundRect(color.copy(alpha = 0.18f), size = Size(size.width, size.height))
                drawRoundRect(color, size = Size(size.width * barFraction, size.height))
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = MaldarDesign.spacing.sm))
                transactions.sortedByDescending { it.dateMillis }.forEach { tx ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(tx.note.ifBlank { JalaliCalendar.formatDate(tx.dateMillis) }, style = MaterialTheme.typography.bodySmall)
                            Text(JalaliCalendar.formatDateTime(tx.dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${Money.format(tx.amount)} ${AppStrings.moneyUnit}", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
