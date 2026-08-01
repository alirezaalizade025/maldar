package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

@Composable
fun MaldarSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.RadioButton) { onSelected(index) }
                        .semantics { selected = isSelected },
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun MaldarSegmentedControlPreview() = MaldarDesignTheme {
    MaldarSegmentedControl(listOf("هزینه", "درآمد"), 0, {})
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MaldarSegmentedControlDarkPreview() = MaldarDesignTheme(true) {
    MaldarSegmentedControl(listOf("هزینه", "درآمد"), 1, {})
}
