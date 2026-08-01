package com.personalfinance.tracker.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

object MaldarDesign {
    val colors: MaldarSemanticColors
        @Composable @ReadOnlyComposable get() = LocalMaldarSemanticColors.current
    val spacing: MaldarSpacing
        @Composable @ReadOnlyComposable get() = LocalMaldarSpacing.current
}

/** Isolated Phase 1 theme. It is intentionally not installed by the production app yet. */
@Composable
fun MaldarDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMaldarSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
        LocalMaldarSpacing provides MaldarSpacing()
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) MaldarDarkColorScheme else MaldarLightColorScheme,
            typography = MaldarTypography,
            shapes = MaldarShapes,
            content = content
        )
    }
}
