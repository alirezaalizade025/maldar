package com.personalfinance.tracker.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MaldarSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val section: Dp = 32.dp
)

internal val LocalMaldarSpacing = staticCompositionLocalOf { MaldarSpacing() }

object MaldarElevation {
    val flat = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val floating = 6.dp
}
