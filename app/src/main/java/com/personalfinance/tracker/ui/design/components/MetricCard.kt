package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    tone: AmountTone = AmountTone.NEUTRAL
) {
    AppCard(modifier = modifier, style = AppCardStyle.OUTLINED) {
        Column(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
            if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            if (icon != null) Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            AmountText(value, tone = tone, style = MaterialTheme.typography.titleMedium)
            if (supportingText != null) {
                Spacer(Modifier.height(4.dp))
                Text(supportingText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun MetricCardPreview() = MaldarDesignTheme {
    MetricCard("درآمد این ماه", "۸٬۲۰۰٬۰۰۰", icon = Icons.AutoMirrored.Filled.TrendingUp, tone = AmountTone.POSITIVE)
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MetricCardDarkPreview() = MaldarDesignTheme(true) {
    MetricCard("درآمد این ماه", "۸٬۲۰۰٬۰۰۰", tone = AmountTone.POSITIVE)
}
