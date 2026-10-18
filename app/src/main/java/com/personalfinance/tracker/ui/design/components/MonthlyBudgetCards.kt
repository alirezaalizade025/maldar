package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.data.MonthlyBudgetSummary
import com.personalfinance.tracker.data.TransactionEntity
import com.personalfinance.tracker.data.calculateMonthlyBudget
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.Digits
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import com.personalfinance.tracker.util.fa
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@Composable
fun rememberMonthlyBudget(transactions: List<TransactionEntity>, categories: List<String>, monthOffset: Int = 0): MonthlyBudgetSummary {
    // Refresh while visible, including when the app stays open across Jalali month-end.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000)
        }
    }
    return remember(transactions, categories, monthOffset, now) {
        calculateMonthlyBudget(transactions, categories, Calendar.getInstance(), monthOffset)
    }
}

fun LazyListScope.monthlyBudgetItems(summary: MonthlyBudgetSummary) {
    item(key = "monthly-budget-heading") { SectionHeader(AppStrings.monthlyBudget) }
    item(key = "monthly-budget-summary") {
        AppCard(style = AppCardStyle.OUTLINED, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(AppStrings.totalEstimatedBudget, style = MaterialTheme.typography.labelLarge)
                AmountText(Money.format2(summary.estimated), style = MaterialTheme.typography.headlineMedium)
                Text(AppStrings.budgetHistoryHint, style = MaterialTheme.typography.labelSmall)
                Text(
                    AppStrings.budgetUntil.fa(JalaliCalendar.formatDate(summary.endMillis)) + " • " +
                        AppStrings.budgetMonthLength.fa(Digits.toPersian(summary.daysInMonth.toString())),
                    style = MaterialTheme.typography.labelSmall
                )
                BudgetUsage(summary.estimated, summary.spent, summary.usedPercent)
            }
        }
    }
    if (summary.estimated == 0.0 && summary.spent == 0.0) {
        item(key = "monthly-budget-empty") { Text(AppStrings.budgetNoHistoryHint, style = MaterialTheme.typography.bodySmall) }
    } else {
        items(summary.categories, key = { "monthly-budget-category-${it.category}" }) { budget ->
            AppCard(style = AppCardStyle.OUTLINED, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(budget.category, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(AppStrings.budgetEstimate + ": " + Money.format2(budget.estimated), style = MaterialTheme.typography.bodySmall)
                    }
                    BudgetUsage(budget.estimated, budget.spent, budget.usedPercent)
                }
            }
        }
    }
}

@Composable
private fun BudgetUsage(estimated: Double, spent: Double, usedPercent: Double?) {
    val overBudget = estimated > 0.0 && spent > estimated
    val color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val usage = usedPercent?.let { Digits.toPersian(String.format(Locale.US, "%.1f", it)) + "٪" }
        ?: AppStrings.budgetNoEstimate
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(AppStrings.budgetSpent + ": " + Money.format2(spent), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(usage, color = color, style = MaterialTheme.typography.bodySmall)
    }
    if (usedPercent != null) {
        LinearProgressIndicator(
            progress = { (usedPercent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(), color = color
        )
    }
    Text(
        when {
            usedPercent == null -> AppStrings.budgetNoHistoryHint
            overBudget -> AppStrings.budgetOverrun + ": " + Money.format2(spent - estimated) + " " + AppStrings.moneyUnit
            else -> AppStrings.budgetRemaining + ": " + Money.format2(estimated - spent) + " " + AppStrings.moneyUnit
        },
        color = color, style = MaterialTheme.typography.bodySmall
    )
}
