package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesignTheme
import com.personalfinance.tracker.ui.design.MaldarElevation

enum class AppCardStyle { FLAT, OUTLINED, RAISED, HERO }

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    style: AppCardStyle = AppCardStyle.FLAT,
    containerColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = if (style == AppCardStyle.HERO) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
        color = containerColor ?: if (style == AppCardStyle.HERO) colors.primaryContainer else colors.surface,
        contentColor = if (style == AppCardStyle.HERO) colors.onPrimaryContainer else colors.onSurface,
        border = if (style == AppCardStyle.OUTLINED) BorderStroke(1.dp, colors.outlineVariant) else null,
        tonalElevation = when (style) {
            AppCardStyle.FLAT, AppCardStyle.OUTLINED -> MaldarElevation.flat
            AppCardStyle.RAISED -> MaldarElevation.raised
            AppCardStyle.HERO -> MaldarElevation.card
        }
    ) { Box(Modifier.padding(contentPadding)) { content() } }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun AppCardPreview() = MaldarDesignTheme {
    AppCard(style = AppCardStyle.RAISED) { androidx.compose.material3.Text("کارت مال‌دار") }
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppCardDarkPreview() = MaldarDesignTheme(darkTheme = true) {
    AppCard(style = AppCardStyle.HERO) { androidx.compose.material3.Text("موجودی کل") }
}
