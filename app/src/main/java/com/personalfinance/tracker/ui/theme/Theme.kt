package com.personalfinance.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalfinance.tracker.R

val Vazir = FontFamily(
    Font(R.font.vazir_regular, FontWeight.Normal),
    Font(R.font.vazir_bold, FontWeight.Bold),
    Font(R.font.vazir_bold, FontWeight.SemiBold),
    Font(R.font.vazir_bold, FontWeight.Medium)
)

// Brand palette (semantic: green = income/positive, red = expense).
val Emerald = Color(0xFF0F5C42)
val EmeraldDark = Color(0xFF0A3D2D)
val Coral = Color(0xFFC8422F)
val Slate = Color(0xFF1A1D22)
val Cream = Color(0xFFEDEAE3)

// Refined dark palette (Maldar redesign — phase 1).
private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF06231A),
    secondary = Color(0xFFF87171),
    onSecondary = Color(0xFF2A0E0E),
    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE6E9ED),
    surface = Color(0xFF15191E),
    onSurface = Color(0xFFE6E9ED),
    surfaceVariant = Color(0xFF1E242B),
    onSurfaceVariant = Color(0xFF9BA4AE),
    outline = Color(0xFF2A313A),
    outlineVariant = Color(0xFF232A31),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0E0E),
    // Semantic aliases reused by Dashboard/Reports screens.
    secondaryContainer = Color(0xFF1B7A5A),
    tertiaryContainer = Color(0xFFE8604C)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F5C42),
    onPrimary = Color.White,
    secondary = Coral,
    background = Color(0xFFE4E1DA),
    surface = Color(0xFFF2F0EB),
    surfaceVariant = Color(0xFFE7E3DB),
    onBackground = Slate,
    onSurface = Slate,
    onSurfaceVariant = Color(0xFF5A6068),
    outline = Color(0xFFD2CDC3)
)

// Type ramp (Vazir). Bold + color carry hierarchy, not size creep.
val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Vazir, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Vazir, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Vazir, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Vazir, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
)

// Shape tokens (phase 1): rounded, consistent corners.
val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

// 8dp-based spacing scale (phase 1).
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
