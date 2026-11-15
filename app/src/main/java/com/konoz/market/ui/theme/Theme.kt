package com.konoz.market.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Semantic colors that Material3 does not provide (success / warning). */
data class KonozColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val accent: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
) {
    /** Old name of [accent], kept so existing screens keep compiling. */
    val accentLime: Color get() = accent
}

private val LightKonozColors = KonozColors(
    success = Brand.Success,
    onSuccess = Color.White,
    warning = Brand.Warning,
    onWarning = Brand.TextMain,
    accent = Brand.Accent,
    warningContainer = Brand.WarningContainerLight,
    onWarningContainer = Brand.OnWarningContainerLight,
)

private val DarkKonozColors = KonozColors(
    success = Brand.SuccessDarkMode,
    onSuccess = Brand.BackgroundDark,
    warning = Brand.WarningDarkMode,
    onWarning = Brand.BackgroundDark,
    accent = Brand.SecondaryContainerDark,
    warningContainer = Brand.WarningContainerDark,
    onWarningContainer = Brand.OnWarningContainerDark,
)

val LocalKonozColors = staticCompositionLocalOf { LightKonozColors }

private val LightScheme = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Color.White,
    primaryContainer = Brand.Accent,
    onPrimaryContainer = Brand.PrimaryDeep,
    secondary = Brand.Secondary,
    onSecondary = Color.White,
    secondaryContainer = Brand.Accent,
    onSecondaryContainer = Brand.PrimaryDeep,
    tertiary = Brand.Warning,
    onTertiary = Brand.TextMain,
    tertiaryContainer = Brand.WarningContainerLight,
    onTertiaryContainer = Brand.OnWarningContainerLight,
    background = Brand.BackgroundLight,
    onBackground = Brand.TextMain,
    surface = Brand.SurfaceLight,
    onSurface = Brand.TextMain,
    surfaceVariant = Brand.BackgroundLight,
    onSurfaceVariant = Brand.TextSecondary,
    surfaceTint = Brand.Primary,
    error = Brand.Error,
    onError = Color.White,
    errorContainer = Brand.ErrorContainerLight,
    onErrorContainer = Brand.OnErrorContainerLight,
    outline = Brand.OutlineLight,
    outlineVariant = Brand.OutlineVariantLight,
)

private val DarkScheme = darkColorScheme(
    primary = Brand.PrimaryDarkMode,
    onPrimary = Brand.OnPrimaryDark,
    primaryContainer = Brand.PrimaryContainerDark,
    onPrimaryContainer = Brand.Accent,
    secondary = Brand.SecondaryDarkMode,
    onSecondary = Brand.OnSecondaryDark,
    secondaryContainer = Brand.SecondaryContainerDark,
    onSecondaryContainer = Brand.Accent,
    tertiary = Brand.WarningDarkMode,
    onTertiary = Brand.BackgroundDark,
    tertiaryContainer = Brand.WarningContainerDark,
    onTertiaryContainer = Brand.OnWarningContainerDark,
    background = Brand.BackgroundDark,
    onBackground = Brand.TextMainDark,
    surface = Brand.SurfaceDark,
    onSurface = Brand.TextMainDark,
    surfaceVariant = Brand.SurfaceVariantDark,
    onSurfaceVariant = Brand.TextSecondaryDark,
    surfaceTint = Brand.PrimaryDarkMode,
    error = Brand.DangerDarkMode,
    onError = Brand.BackgroundDark,
    errorContainer = Brand.ErrorContainerDark,
    onErrorContainer = Brand.OnErrorContainerDark,
    outline = Brand.OutlineDark,
    outlineVariant = Brand.OutlineVariantDark,
)

@Composable
fun KonozTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val typography = remember(context) { konozTypography(cairoFontFamily(context)) }
    CompositionLocalProvider(
        LocalKonozColors provides if (darkTheme) DarkKonozColors else LightKonozColors,
        // Layout direction follows the app locale automatically (Arabic = RTL).
        LocalLayoutDirection provides LocalLayoutDirection.current,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = typography,
            content = content,
        )
    }
}

object KonozTheme_ {
    val colors: KonozColors
        @Composable get() = LocalKonozColors.current
}
