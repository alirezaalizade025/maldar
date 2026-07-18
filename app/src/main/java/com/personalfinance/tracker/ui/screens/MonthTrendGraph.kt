package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalfinance.tracker.ui.theme.Coral
import com.personalfinance.tracker.ui.theme.Emerald
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.JalaliCalendar
import com.personalfinance.tracker.util.Money
import java.util.Calendar

/**
 * Simple grouped bar chart of the last months: income (emerald) vs
 * expense (coral). Data is oldest -> newest.
 *
 * A summary (total income / expense / net) is shown above the chart. Tapping a
 * month group reveals a tooltip with that month's details (income, expense, net
 * and balance). Used as the "hover to see more" interaction on touch devices.
 */
@Composable
fun MonthTrendGraph(
    data: List<Pair<Double, Double>>,
    balanceLine: List<Double> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    val labelInfo = remember(data.size) {
        val cal = Calendar.getInstance()
        (data.size - 1 downTo 0).map { back ->
            JalaliCalendar.monthLabel(cal, -back)
        }
    }

    val labelLayouts = remember(labelInfo, labelColor) {
        labelInfo.map { measurer.measure(AnnotatedString(it), TextStyle(fontSize = 10.sp, color = labelColor)) }
    }

    val totalIncome = remember(data) { data.sumOf { it.first } }
    val totalExpense = remember(data) { data.sumOf { it.second } }
    val totalNet = totalIncome - totalExpense

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary

    val maxVal = (data.maxOfOrNull { maxOf(it.first, it.second) } ?: 0.0).coerceAtLeast(1.0)

    // Net-worth line: scale to its own range so it's visible regardless of magnitude.
    val hasBalance = balanceLine.size == data.size && balanceLine.isNotEmpty()
    val balMin = balanceLine.minOrNull() ?: 0.0
    val balMax = balanceLine.maxOrNull() ?: 0.0
    val balRange = (balMax - balMin).coerceAtLeast(1.0)

    Column(modifier.fillMaxWidth()) {
        // ---- Summary header ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(AppStrings.reportIncome, totalIncome, Emerald)
            SummaryItem(AppStrings.reportExpense, totalExpense, Coral)
            SummaryItem(AppStrings.net, totalNet, MaterialTheme.colorScheme.onSurface)
        }

        val sidePad = 8.dp
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(data.size) {
                    detectTapGestures { offset ->
                        val width = size.width
                        val usable = width - sidePad.toPx() * 2
                        val groupW = usable / data.size
                        val idx = ((offset.x - sidePad.toPx()) / groupW).toInt()
                        selectedIndex = if (idx in data.indices) idx else null
                    }
                }
        ) {
            val sidePadPx = sidePad.toPx()
            val gap = 12.dp.toPx()
            val groupW = (size.width - sidePadPx * 2) / data.size
            val barW = (groupW - gap) / 2
            val baseY = size.height - 18.dp.toPx()

            data.forEachIndexed { i, (inc, exp) ->
                val groupX = sidePadPx + i * groupW
                val incH = (inc / maxVal * (baseY - 6.dp.toPx())).toFloat()
                val expH = (exp / maxVal * (baseY - 6.dp.toPx())).toFloat()

                drawRoundRect(
                    color = Emerald,
                    topLeft = Offset(groupX, baseY - incH),
                    size = Size(barW, incH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = Coral,
                    topLeft = Offset(groupX + barW + 4.dp.toPx(), baseY - expH),
                    size = Size(barW, expH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )

                // Net (income - expense) marker line above the group.
                val net = inc - exp
                val netH = (net / maxVal * (baseY - 6.dp.toPx())).toFloat()
                drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFF5A5F66),
                    start = Offset(groupX + groupW / 2, baseY - maxOf(incH, expH) - 6.dp.toPx()),
                    end = Offset(groupX + groupW / 2, baseY - maxOf(incH, expH) - 6.dp.toPx() - netH),
                    strokeWidth = 2.dp.toPx()
                )

                val layout = labelLayouts[i]
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        groupX + groupW / 2 - layout.size.width / 2,
                        baseY + 2.dp.toPx()
                    )
                )

                // Highlight ring on the selected group.
                if (i == selectedIndex) {
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.25f),
                        topLeft = Offset(groupX - 4.dp.toPx(), 2.dp.toPx()),
                        size = Size(groupW + 8.dp.toPx(), baseY - 2.dp.toPx() + 16.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                }
            }

            // Net-worth (balance over time) polyline, scaled to its own range and
            // drawn in the upper region of the chart.
            if (hasBalance) {
                val top = 6.dp.toPx()
                val bottom = baseY - 6.dp.toPx()
                val lineH = bottom - top
                val points = balanceLine.mapIndexed { i, v ->
                    val groupX = sidePadPx + i * groupW
                    val x = groupX + groupW / 2
                    val y = bottom - ((v - balMin) / balRange * lineH).toFloat()
                    Offset(x, y)
                }
                for (i in 1 until points.size) {
                    drawLine(
                        color = androidx.compose.ui.graphics.Color(0xFF2B6CB0),
                        start = points[i - 1],
                        end = points[i],
                        strokeWidth = 3.dp.toPx()
                    )
                }
                points.forEach { p ->
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.Color(0xFF2B6CB0),
                        topLeft = Offset(p.x - 3.dp.toPx(), p.y - 3.dp.toPx()),
                        size = Size(6.dp.toPx(), 6.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                }
            }

            // ---- Tooltip for the selected month ----
            selectedIndex?.let { idx ->
                val (inc, exp) = data[idx]
                val net = inc - exp
                val bal = if (hasBalance) balanceLine[idx] else null
                val lines = buildList {
                    add(labelInfo[idx])
                    add("${AppStrings.reportIncome}: ${Money.format(inc)}")
                    add("${AppStrings.reportExpense}: ${Money.format(exp)}")
                    add("${AppStrings.net}: ${Money.format(net)}")
                    bal?.let { add("${AppStrings.balanceTrend}: ${Money.format(it)}") }
                }
                val textStyle = TextStyle(fontSize = 11.sp, color = Color.White)
                val measured = lines.map { measurer.measure(AnnotatedString(it), textStyle) }
                val boxW = (measured.maxOfOrNull { it.size.width } ?: 0) + 20.dp.toPx()
                val boxH = measured.sumOf { (it.size.height + 4.dp.toPx()).toDouble() } + 12.dp.toPx()

                val groupX = sidePadPx + idx * groupW
                var boxX = groupX + groupW / 2 - boxW / 2
                boxX = boxX.coerceIn(2.dp.toPx(), size.width - boxW - 2.dp.toPx())
                val boxY = 4.dp.toPx()

                drawRoundRect(
                    color = Color(0xFF1F2933),
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxW, boxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )
                var y = boxY + 8.dp.toPx()
                measured.forEach { tl ->
                    drawText(
                        textLayoutResult = tl,
                        topLeft = Offset(boxX + 10.dp.toPx(), y)
                    )
                    y += tl.size.height + 4.dp.toPx()
                }
            }
        }
    }
}

@Composable
private fun RowScope.SummaryItem(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(
            Money.format(amount) + " " + AppStrings.moneyUnit,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = color
        )
    }
}
