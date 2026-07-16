package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
 * Simple grouped bar chart of the last [months] months: income (emerald) vs
 * expense (coral). Data is oldest -> newest.
 */
@Composable
fun MonthTrendGraph(
    data: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    val measurer = rememberTextMeasurer()

    val labels = remember(data.size) {
        val cal = Calendar.getInstance()
        (data.size - 1 downTo 0).map { back ->
            JalaliCalendar.monthLabel(cal, -back)
        }
    }

    val maxVal = (data.maxOfOrNull { maxOf(it.first, it.second) } ?: 0.0).coerceAtLeast(1.0)

    Column(modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val barAreaW = size.width - 8.dp.toPx() * 2
            val gap = 12.dp.toPx()
            val groupW = barAreaW / data.size
            val barW = (groupW - gap) / 2
            val baseY = size.height - 18.dp.toPx()

            data.forEachIndexed { i, (inc, exp) ->
                val groupX = 8.dp.toPx() + i * groupW
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

                val label = labels[i]
                val text = measurer.measure(
                    label,
                    TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                )
                drawText(
                    text,
                    topLeft = Offset(
                        groupX + groupW / 2 - text.size.width / 2,
                        baseY + 2.dp.toPx()
                    )
                )
            }
        }
    }
}
