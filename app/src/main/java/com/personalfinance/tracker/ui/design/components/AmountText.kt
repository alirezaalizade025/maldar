package com.personalfinance.tracker.ui.design.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

enum class AmountTone { NEUTRAL, POSITIVE, NEGATIVE }

@Composable
fun AmountText(
    amount: String,
    modifier: Modifier = Modifier,
    currency: String = "تومان",
    tone: AmountTone = AmountTone.NEUTRAL,
    showSign: Boolean = false,
    style: TextStyle = MaterialTheme.typography.titleMedium
) {
    val prefix = when {
        !showSign -> ""
        tone == AmountTone.POSITIVE -> "+ "
        tone == AmountTone.NEGATIVE -> "− "
        else -> ""
    }
    val color: Color = when (tone) {
        AmountTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        AmountTone.POSITIVE -> MaldarDesign.colors.positive
        AmountTone.NEGATIVE -> MaldarDesign.colors.negative
    }
    val value = "$prefix$amount $currency"
    Text(
        text = value,
        modifier = modifier.semantics { contentDescription = value },
        color = color,
        style = style,
        fontWeight = FontWeight.Bold
    )
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun AmountTextPreview() = MaldarDesignTheme {
    AmountText("۸٬۲۰۰٬۰۰۰", tone = AmountTone.POSITIVE, showSign = true)
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AmountTextDarkPreview() = MaldarDesignTheme(true) {
    AmountText("۴۵۰٬۰۰۰", tone = AmountTone.NEGATIVE, showSign = true)
}
