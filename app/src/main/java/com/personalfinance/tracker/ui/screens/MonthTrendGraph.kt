package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalfinance.tracker.ui.theme.Coral
import com.personalfinance.tracker.ui.theme.Emerald
import com.personalfinance.tracker.util.JalaliCalendar
import java.util.Calendar

/**
 * Simple grouped bar chart of the last months: income (emerald) vs
 * expense (coral). Data is oldest -> newest.
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

    val maxVal = (data.maxOfOrNull { maxOf(it.first, it.second) } ?: 0.0).coerceAtLeast(1.0)

    // Net-worth line: scale to its own range so it's visible regardless of magnitude.
    val hasBalance = balanceLine.size == data.size && balanceLine.isNotEmpty()
    val balMin = balanceLine.minOrNull() ?: 0.0
    val balMax = balanceLine.maxOrNull() ?: 0.0
    val balRange = (balMax - balMin).coerceAtLeast(1.0)

    Column(modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val sidePad = 8.dp.toPx()
            val gap = 12.dp.toPx()
            val groupW = (size.width - sidePad * 2) / data.size
            val barW = (groupW - gap) / 2
            val baseY = size.height - 18.dp.toPx()

        data.forEachIndexed { i, (inc, exp) ->
            val groupX = sidePad + i * groupW
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
        }

        // Net-worth (balance over time) polyline, scaled to its own range and
        // drawn in the upper region of the chart.
        if (hasBalance) {
            val top = 6.dp.toPx()
            val bottom = baseY - 6.dp.toPx()
            val lineH = bottom - top
            val points = balanceLine.mapIndexed { i, v ->
                val groupX = sidePad + i * groupW
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
    }
}
}
