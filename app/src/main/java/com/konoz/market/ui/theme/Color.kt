package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design Tokens - the ONLY place raw colors are defined.
 * Screens must use MaterialTheme.colorScheme or KonozColors, never Color(0x...).
 */
object Brand {
    val PrimaryGreen = Color(0xFF16865B)
    val DarkGreen = Color(0xFF105C43)
    val FreshLime = Color(0xFFB6DC55)
    val WarmYellow = Color(0xFFFFC857)
    val DangerRed = Color(0xFFD94343)
    val SuccessGreen = Color(0xFF16865B)

    // Light surfaces
    val BackgroundLight = Color(0xFFF7F9F5)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextMain = Color(0xFF202A25)
    val TextSecondary = Color(0xFF68756D)

    // Dark surfaces
    val BackgroundDark = Color(0xFF0F1713)
    val SurfaceDark = Color(0xFF18231D)
    val SurfaceVariantDark = Color(0xFF22302A)
    val TextMainDark = Color(0xFFEAF2EC)
    val TextSecondaryDark = Color(0xFFB4C2B9)
    val PrimaryDarkMode = Color(0xFF4CC79A)
    val DangerDarkMode = Color(0xFFFF7A7A)

    val OutlineLight = Color(0xFFCBD5CE)
    val OutlineDark = Color(0xFF3A4A41)
}
