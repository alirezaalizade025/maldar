package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

@Composable
fun TransactionRow(
    title: String,
    amount: String,
    metadata: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ShoppingCart,
    tone: AmountTone = AmountTone.NEUTRAL,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    Row(
        clickableModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = when (tone) {
            AmountTone.POSITIVE -> MaldarDesign.colors.positive
            AmountTone.NEGATIVE -> MaldarDesign.colors.negative
            AmountTone.NEUTRAL -> MaterialTheme.colorScheme.primary
        }
        Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        AmountText(amount, tone = tone, showSign = tone != AmountTone.NEUTRAL, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun TransactionRowPreview() = MaldarDesignTheme {
    TransactionRow("خرید روزانه", "۴۵۰٬۰۰۰", "بانک ملت • امروز", tone = AmountTone.NEGATIVE)
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransactionRowDarkPreview() = MaldarDesignTheme(true) {
    TransactionRow("حقوق ماهانه", "۸٬۲۰۰٬۰۰۰", "بانک ملت • امروز", tone = AmountTone.POSITIVE)
}
