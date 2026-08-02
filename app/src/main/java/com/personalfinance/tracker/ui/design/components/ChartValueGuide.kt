package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.personalfinance.tracker.util.Money

/** Numeric Y-axis guide shared by report charts. */
@Composable
fun ChartValueGuide(maxValue: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        listOf(maxValue, maxValue * 2.0 / 3.0, maxValue / 3.0, 0.0).forEach { value ->
            Text(
                text = Money.format(value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
