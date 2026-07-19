package com.personalfinance.tracker.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Shared card used across Maldar screens (redesign phase 2). Replaces the
 * repeated `Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp)`
 * pattern so every card inherits the theme shape + consistent elevation.
 *
 * [raised] uses the surface-variant fill for hero cards. [filled] tints with a
 * custom color (used for the primary balance hero). [contentPadding] defaults
 * to 0 so it is a drop-in replacement for existing cards whose inner Column
 * already supplies its own padding.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    filled: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (raised || filled != null) 2.dp else 1.dp,
        color = filled ?: if (raised) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        content = {
            Box(modifier = Modifier.padding(contentPadding)) { content() }
        }
    )
}
