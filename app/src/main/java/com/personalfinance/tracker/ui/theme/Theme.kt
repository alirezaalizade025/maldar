package com.personalfinance.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Emerald = Color(0xFF0F5C42)
val EmeraldDark = Color(0xFF0A3D2D)
val Coral = Color(0xFFC8422F)
val Slate = Color(0xFF1A1D22)
val Cream = Color(0xFFEDEAE3)

private val LightColors = lightColorScheme(
    primary = Emerald,
    secondary = Coral,
    background = Color(0xFFE4E1DA),
    surface = Color(0xFFF2F0EB),
    onPrimary = Color.White,
    onBackground = Slate,
    onSurface = Slate
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2E8B66),
    secondary = Coral,
    background = Color(0xFF0C0E10),
    surface = Color(0xFF16191C),
    onSurface = Color(0xFFD7DBDF),
    onSurfaceVariant = Color(0xFF9AA1A8)
)

val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun PersonalFinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
