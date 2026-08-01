package com.personalfinance.tracker.ui.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object MaldarPalette {
    val Teal = Color(0xFF0AAE91)
    val TealDark = Color(0xFF087866)
    val Blue = Color(0xFF2F8DF4)
    val Positive = Color(0xFF159447)
    val Negative = Color(0xFFDC3F3F)
    val Warning = Color(0xFFE38A00)
    val WarningContainer = Color(0xFFFFF4D6)
    val LightBackground = Color(0xFFF3F6FA)
    val LightSurfaceVariant = Color(0xFFE8EEF7)
    val LightOutline = Color(0xFFD5DAE1)
    val LightText = Color(0xFF111315)
    val LightTextSecondary = Color(0xFF656A70)
    val DarkBackground = Color(0xFF0D0F12)
    val DarkSurface = Color(0xFF191B20)
    val DarkSurfaceVariant = Color(0xFF202228)
    val DarkOutline = Color(0xFF2A2D33)
    val DarkText = Color(0xFFF4F4F5)
    val DarkTextSecondary = Color(0xFF9A9DA4)
    val DarkPositive = Color(0xFF35C47B)
    val DarkNegative = Color(0xFFF05B58)
    val DarkWarning = Color(0xFFF4C430)
}

internal val MaldarLightColorScheme = lightColorScheme(
    primary = MaldarPalette.TealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5EE),
    onPrimaryContainer = Color(0xFF00382F),
    secondary = MaldarPalette.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAFF),
    onSecondaryContainer = Color(0xFF082E5A),
    tertiary = MaldarPalette.Positive,
    onTertiary = Color.White,
    background = MaldarPalette.LightBackground,
    onBackground = MaldarPalette.LightText,
    surface = Color.White,
    onSurface = MaldarPalette.LightText,
    surfaceVariant = MaldarPalette.LightSurfaceVariant,
    onSurfaceVariant = MaldarPalette.LightTextSecondary,
    outline = MaldarPalette.LightOutline,
    outlineVariant = Color(0xFFE3E7EC),
    error = MaldarPalette.Negative,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

internal val MaldarDarkColorScheme = darkColorScheme(
    primary = MaldarPalette.DarkPositive,
    onPrimary = Color(0xFF003921),
    primaryContainer = Color(0xFF075C43),
    onPrimaryContainer = Color(0xFFA8F2D0),
    secondary = Color(0xFF82B8FF),
    onSecondary = Color(0xFF00315F),
    secondaryContainer = Color(0xFF174A78),
    onSecondaryContainer = Color(0xFFD6E7FF),
    tertiary = MaldarPalette.DarkWarning,
    onTertiary = Color(0xFF3B2F00),
    background = MaldarPalette.DarkBackground,
    onBackground = MaldarPalette.DarkText,
    surface = MaldarPalette.DarkSurface,
    onSurface = MaldarPalette.DarkText,
    surfaceVariant = MaldarPalette.DarkSurfaceVariant,
    onSurfaceVariant = MaldarPalette.DarkTextSecondary,
    outline = MaldarPalette.DarkOutline,
    outlineVariant = Color(0xFF23262C),
    error = MaldarPalette.DarkNegative,
    onError = Color(0xFF3B0909),
    errorContainer = Color(0xFF6D2524),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Immutable
data class MaldarSemanticColors(
    val positive: Color,
    val onPositive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val onNegative: Color,
    val negativeContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color
)

internal val LightSemanticColors = MaldarSemanticColors(
    positive = MaldarPalette.Positive,
    onPositive = Color.White,
    positiveContainer = Color(0xFFDDF6E6),
    negative = MaldarPalette.Negative,
    onNegative = Color.White,
    negativeContainer = Color(0xFFFFE2E0),
    warning = MaldarPalette.Warning,
    onWarning = Color(0xFF3D2600),
    warningContainer = MaldarPalette.WarningContainer
)

internal val DarkSemanticColors = MaldarSemanticColors(
    positive = MaldarPalette.DarkPositive,
    onPositive = Color(0xFF002F1B),
    positiveContainer = Color(0xFF123C2D),
    negative = MaldarPalette.DarkNegative,
    onNegative = Color(0xFF3B0909),
    negativeContainer = Color(0xFF482322),
    warning = MaldarPalette.DarkWarning,
    onWarning = Color(0xFF352A00),
    warningContainer = Color(0xFF3C3417)
)

internal val LocalMaldarSemanticColors = staticCompositionLocalOf { LightSemanticColors }
