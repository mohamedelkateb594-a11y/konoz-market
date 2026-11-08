package com.konoz.market.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Semantic colors that Material3 does not provide (success / warning). */
data class KonozColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val accentLime: Color,
)

private val LightKonozColors = KonozColors(
    success = Brand.SuccessGreen,
    onSuccess = Color.White,
    warning = Brand.WarmYellow,
    onWarning = Brand.TextMain,
    accentLime = Brand.FreshLime,
)

private val DarkKonozColors = KonozColors(
    success = Brand.PrimaryDarkMode,
    onSuccess = Brand.BackgroundDark,
    warning = Brand.WarmYellow,
    onWarning = Brand.BackgroundDark,
    accentLime = Brand.FreshLime,
)

val LocalKonozColors = staticCompositionLocalOf { LightKonozColors }

private val LightScheme = lightColorScheme(
    primary = Brand.PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Brand.FreshLime,
    onPrimaryContainer = Brand.TextMain,
    secondary = Brand.DarkGreen,
    onSecondary = Color.White,
    tertiary = Brand.WarmYellow,
    onTertiary = Brand.TextMain,
    background = Brand.BackgroundLight,
    onBackground = Brand.TextMain,
    surface = Brand.SurfaceLight,
    onSurface = Brand.TextMain,
    surfaceVariant = Brand.BackgroundLight,
    onSurfaceVariant = Brand.TextSecondary,
    error = Brand.DangerRed,
    onError = Color.White,
    outline = Brand.OutlineLight,
)

private val DarkScheme = darkColorScheme(
    primary = Brand.PrimaryDarkMode,
    onPrimary = Brand.BackgroundDark,
    primaryContainer = Brand.DarkGreen,
    onPrimaryContainer = Brand.TextMainDark,
    secondary = Brand.FreshLime,
    onSecondary = Brand.BackgroundDark,
    tertiary = Brand.WarmYellow,
    onTertiary = Brand.BackgroundDark,
    background = Brand.BackgroundDark,
    onBackground = Brand.TextMainDark,
    surface = Brand.SurfaceDark,
    onSurface = Brand.TextMainDark,
    surfaceVariant = Brand.SurfaceVariantDark,
    onSurfaceVariant = Brand.TextSecondaryDark,
    error = Brand.DangerDarkMode,
    onError = Brand.BackgroundDark,
    outline = Brand.OutlineDark,
)

@Composable
fun KonozTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKonozColors provides if (darkTheme) DarkKonozColors else LightKonozColors,
        // Layout direction follows the app locale automatically (Arabic = RTL).
        LocalLayoutDirection provides LocalLayoutDirection.current,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = KonozTypography,
            content = content,
        )
    }
}

object KonozTheme_ {
    val colors: KonozColors
        @Composable get() = LocalKonozColors.current
}
