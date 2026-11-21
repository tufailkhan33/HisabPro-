package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HisabLightColorScheme = lightColorScheme(
    primary = HisabNavy,
    onPrimary = HisabWhite,
    primaryContainer = HisabNavyContainer,
    onPrimaryContainer = HisabNavy,
    secondary = HisabNavyLight,
    onSecondary = HisabWhite,
    secondaryContainer = HisabSurfaceVariant,
    onSecondaryContainer = HisabNavy,
    tertiary = HisabSuccessGreen,
    onTertiary = HisabWhite,
    tertiaryContainer = HisabSuccessBg,
    onTertiaryContainer = HisabSuccessGreen,
    background = HisabLightGray,
    onBackground = HisabBlackText,
    surface = HisabWhite,
    onSurface = HisabBlackText,
    surfaceVariant = HisabSurfaceVariant,
    onSurfaceVariant = HisabSecondaryGray,
    outline = HisabBorder,
    outlineVariant = HisabBorder,
    error = HisabErrorRed,
    onError = HisabWhite,
    errorContainer = HisabErrorBg,
    onErrorContainer = HisabErrorRed
)

private val HisabDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8DB4E2),
    onPrimary = HisabNavyDark,
    primaryContainer = HisabNavy,
    onPrimaryContainer = HisabWhite,
    secondary = Color(0xFFAEC6E3),
    onSecondary = HisabNavyDark,
    background = Color(0xFF0C1420),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF142033),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1D2D44),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF2C3E57),
    error = Color(0xFFF27373),
    onError = Color(0xFF3B0808)
)

val HisabProShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun HisabProTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HisabDarkColorScheme else HisabLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HisabProTypography,
        shapes = HisabProShapes,
        content = content
    )
}
