package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

private val AppDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = RoyalPurple,
    onSecondary = Color.White,
    secondaryContainer = RoyalPurpleDark,
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = CyanAccent,
    onTertiary = Color(0xFF042F2E),
    background = ObsidianBackground,
    onBackground = DarkTextPrimary,
    surface = MidnightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightCard,
    surfaceContainerHigh = MidnightCardElevated,
    outline = MidnightBorder,
    outlineVariant = MidnightBorder.copy(alpha = 0.6f),
    error = RoseError,
    onError = Color.White
)

private val AppLightColorScheme = lightColorScheme(
    primary = ElectricBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = RoyalPurpleDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = Color(0xFF0891B2),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCardElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightCardElevated,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.7f),
    error = Color(0xFFE11D48),
    onError = Color.White
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Immutable
data class BrandColors(
    val isDark: Boolean,
    val primaryGradient: Brush,
    val heroGlowBrush: Brush,
    val cardBorder: Color,
    val success: Color,
    val warning: Color,
    val cardGlassSurface: Color
)

val LocalBrandColors = staticCompositionLocalOf {
    BrandColors(
        isDark = true,
        primaryGradient = Brush.linearGradient(listOf(ElectricBlue, RoyalPurple)),
        heroGlowBrush = Brush.linearGradient(listOf(ElectricBlue.copy(alpha = 0.2f), RoyalPurple.copy(alpha = 0.2f))),
        cardBorder = MidnightBorder,
        success = EmeraldSuccess,
        warning = AmberWarning,
        cardGlassSurface = MidnightCard
    )
}

@Composable
fun VideoDownloaderProTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) AppDarkColorScheme else AppLightColorScheme
    val brandColors = if (isDark) {
        BrandColors(
            isDark = true,
            primaryGradient = Brush.linearGradient(listOf(ElectricBlue, RoyalPurple)),
            heroGlowBrush = Brush.linearGradient(
                listOf(ElectricBlue.copy(alpha = 0.22f), RoyalPurple.copy(alpha = 0.24f))
            ),
            cardBorder = MidnightBorder,
            success = EmeraldSuccess,
            warning = AmberWarning,
            cardGlassSurface = MidnightCard
        )
    } else {
        BrandColors(
            isDark = false,
            primaryGradient = Brush.linearGradient(listOf(ElectricBlueDark, RoyalPurpleDark)),
            heroGlowBrush = Brush.linearGradient(
                listOf(ElectricBlue.copy(alpha = 0.12f), RoyalPurple.copy(alpha = 0.14f))
            ),
            cardBorder = LightBorder,
            success = Color(0xFF059669),
            warning = Color(0xFFD97706),
            cardGlassSurface = LightCard
        )
    }

    CompositionLocalProvider(LocalBrandColors provides brandColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
