package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design Tokens - the ONLY place raw colors are defined.
 * Screens must use MaterialTheme.colorScheme or KonozColors, never Color(0x...).
 *
 * Identity: Modern Blue (owner decision 2026-11-15). Light and dark values are tuned separately, the dark theme is not an inverted light one.
 */
object Brand {
    // ---- Brand blues (light theme) ----
    val Primary = Color(0xFF174EA6)
    val PrimaryDeep = Color(0xFF0F3A7D)
    val Secondary = Color(0xFF4285F4)
    val Accent = Color(0xFFE8F0FE)

    // ---- Light surfaces and text ----
    val BackgroundLight = Color(0xFFF5F7FB)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextMain = Color(0xFF1F2937)
    val TextSecondary = Color(0xFF64748B)
    val OutlineLight = Color(0xFFD5DCE8)
    val OutlineVariantLight = Color(0xFFE5EAF2)

    // ---- Semantic (light): white text on success/error stays readable, dark text on warning ----
    val Success = Color(0xFF0F8A5F)
    val Warning = Color(0xFFE9A23B)
    val Error = Color(0xFFC93C3C)
    val WarningContainerLight = Color(0xFFFFF1D6)
    val OnWarningContainerLight = Color(0xFF5C3B00)
    val ErrorContainerLight = Color(0xFFFDE8E8)
    val OnErrorContainerLight = Color(0xFF7A1F1F)

    // ---- Dark theme ----
    val BackgroundDark = Color(0xFF0D1422)
    val SurfaceDark = Color(0xFF141D2E)
    val SurfaceVariantDark = Color(0xFF1D2940)
    val TextMainDark = Color(0xFFE6ECF7)
    val TextSecondaryDark = Color(0xFFA8B5CC)
    val OutlineDark = Color(0xFF33425E)
    val OutlineVariantDark = Color(0xFF263349)
    val PrimaryDarkMode = Color(0xFF8AB4F8)
    val OnPrimaryDark = Color(0xFF0B1F44)
    val PrimaryContainerDark = Color(0xFF1E3E78)
    val SecondaryDarkMode = Color(0xFF669DF6)
    val OnSecondaryDark = Color(0xFF08234D)
    val SecondaryContainerDark = Color(0xFF1D2F52)

    // ---- Semantic (dark) ----
    val SuccessDarkMode = Color(0xFF5BD6A0)
    val WarningDarkMode = Color(0xFFF5B84A)
    val DangerDarkMode = Color(0xFFFF8A8A)
    val WarningContainerDark = Color(0xFF4A3510)
    val OnWarningContainerDark = Color(0xFFFFE2A8)
    val ErrorContainerDark = Color(0xFF5A1F1F)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    // ---- Legacy names, kept so existing screens keep compiling. They now carry the blue identity. Prefer the names above. ----
    val PrimaryGreen = Primary
    val DarkGreen = PrimaryDeep
    val FreshLime = Accent
    val WarmYellow = Warning
    val DangerRed = Error
    val SuccessGreen = Success
}
