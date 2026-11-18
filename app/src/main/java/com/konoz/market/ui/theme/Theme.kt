package com.konoz.market.ui.theme

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

/** Semantic colors that Material3 does not provide (success / warning / info and the sign-in screen accent). */
data class KonozColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val accent: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val signInAccent: Color,
    val onSignInAccent: Color,
    val signInHeader: Color,
    val isDark: Boolean,
) {
    /** Old name of [accent], kept so existing screens keep compiling. */
    val accentLime: Color get() = accent
}

private val LightKonozColors = KonozColors(
    success = Brand.Success,
    onSuccess = Brand.OnBrand,
    warning = Brand.Warning,
    onWarning = Brand.TextMain,
    accent = Brand.Accent,
    warningContainer = Brand.WarningContainerLight,
    onWarningContainer = Brand.OnWarningContainerLight,
    info = Brand.Info,
    onInfo = Brand.OnBrand,
    signInAccent = Brand.SignInAccentLight,
    onSignInAccent = Brand.OnBrand,
    signInHeader = Brand.SignInHeaderLight,
    isDark = false,
)

private val DarkKonozColors = KonozColors(
    success = Brand.SuccessDarkMode,
    onSuccess = Brand.BackgroundDark,
    warning = Brand.WarningDarkMode,
    onWarning = Brand.BackgroundDark,
    accent = Brand.SecondaryContainerDark,
    warningContainer = Brand.WarningContainerDark,
    onWarningContainer = Brand.OnWarningContainerDark,
    info = Brand.InfoDarkMode,
    onInfo = Brand.BackgroundDark,
    signInAccent = Brand.SignInAccentDark,
    onSignInAccent = Brand.BackgroundDark,
    signInHeader = Brand.SurfaceVariantDark,
    isDark = true,
)

val LocalKonozColors = staticCompositionLocalOf { LightKonozColors }

// Every role is set on purpose, including the surface containers (Card, chips, sheets read them): the Material defaults for the
// ones left out are a purple-gray that does not belong to the palette.
private val LightScheme = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.OnBrand,
    primaryContainer = Brand.Accent,
    onPrimaryContainer = Brand.PrimaryDeep,
    secondary = Brand.Secondary,
    onSecondary = Brand.OnBrand,
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
    surfaceVariant = Brand.SurfaceVariantLight,
    onSurfaceVariant = Brand.TextSecondary,
    surfaceTint = Brand.Primary,
    surfaceContainerLowest = Brand.ContainerLowestLight,
    surfaceContainerLow = Brand.ContainerLowLight,
    surfaceContainer = Brand.ContainerLight,
    surfaceContainerHigh = Brand.ContainerHighLight,
    surfaceContainerHighest = Brand.ContainerHighestLight,
    surfaceBright = Brand.SurfaceLight,
    surfaceDim = Brand.ContainerLight,
    error = Brand.Error,
    onError = Brand.OnBrand,
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
    surfaceContainerLowest = Brand.ContainerLowestDark,
    surfaceContainerLow = Brand.ContainerLowDark,
    surfaceContainer = Brand.ContainerDark,
    surfaceContainerHigh = Brand.ContainerHighDark,
    surfaceContainerHighest = Brand.ContainerHighestDark,
    surfaceBright = Brand.ContainerHighDark,
    surfaceDim = Brand.BackgroundDark,
    error = Brand.DangerDarkMode,
    onError = Brand.BackgroundDark,
    errorContainer = Brand.ErrorContainerDark,
    onErrorContainer = Brand.OnErrorContainerDark,
    outline = Brand.OutlineDark,
    outlineVariant = Brand.OutlineVariantDark,
)

/**
 * [darkTheme] is decided by the caller from the user's choice (system / light / dark, see [ThemePreference]); the default
 * follows the phone. Everything below reads colors from the scheme, so a switch repaints every screen without recreating the activity.
 */
@Composable
fun KonozTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
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
