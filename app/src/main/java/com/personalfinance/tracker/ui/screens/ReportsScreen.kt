package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    LaunchedEffect(monthOffset) {
        runCatching {
            val (inc, exp) = viewModel.monthlyIncomeExpense(monthOffset)
            income = inc; expense = exp
            breakdown = viewModel.categoryBreakdown(monthOffset)
        }
    }

    val monthLabel = remember(monthOffset) {
        JalaliCalendar.monthLabel(Calendar.getInstance(), monthOffset)
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(AppStrings.reports, style = MaterialTheme.typography.headlineMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { monthOffset-- }) { Text(AppStrings.prev) }
            Text(monthLabel, style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { monthOffset++ }, enabled = monthOffset < 0) { Text(AppStrings.next) }
        }

        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(AppStrings.reportIncome, style = MaterialTheme.typography.labelSmall)
                    Text(Money.format(income) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFF1B7A5A))
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(AppStrings.reportExpense, style = MaterialTheme.typography.labelSmall)
                    Text(Money.format(expense) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold, color = Color(0xFFE8604C))
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(AppStrings.net, style = MaterialTheme.typography.labelSmall)
                    Text(Money.format(income - expense) + " " + AppStrings.moneyUnit, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(AppStrings.spendingByCategory, style = MaterialTheme.typography.titleLarge)

        if (breakdown.isEmpty()) {
            Text(AppStrings.noExpenses, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            val maxVal = breakdown.maxOf { it.total }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                breakdown.forEachIndexed { index, item ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.category, style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(item.total) + " " + AppStrings.moneyUnit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        val fraction = (item.total / maxVal).toFloat().coerceIn(0.02f, 1f)
                        val color = chartColors[index % chartColors.size]
                        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                            drawRoundRect(
                                color = color.copy(alpha = 0.2f),
                                size = Size(size.width, size.height)
                            )
                            drawRoundRect(
                                color = color,
                                size = Size(size.width * fraction, size.height)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
