package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens of the dashboard cards (the strip of items on the home screen): mint and cream tints.
 * The cards are light surfaces in both light and dark mode, so their text and icon colors are fixed on purpose
 * (dark navy on a light tint); they do not follow the dark color scheme. ThemeContrastTest guards every pair.
 */
object ReportCardTokens {
    /** Card name: dark navy, drawn bold. */
    val Title = Color(0xFF26364A)

    /** Short description: blue-gray. */
    val Description = Color(0xFF52647A)

    /** Default icon color, also the outline of the selected card: deep mint. */
    val IconDefault = Color(0xFF0B6B52)

    /** Translucent badge behind the icon (an icon background, never behind text). */
    val IconBadge = Color(0xB3FFFFFF)

    /** Light gradient (start to end) and a matching icon color. */
    class Style(val gradientStart: Color, val gradientEnd: Color, val icon: Color)

    val styles: List<Style> = listOf(
        Style(Color(0xFFE6F7EE), Color(0xFFCFF0E0), IconDefault), // mint
        Style(Color(0xFFFFF8E6), Color(0xFFF7EBC8), Color(0xFF8A5300)), // cream
        Style(Color(0xFFE1F5EC), Color(0xFFC6EBD9), Color(0xFF187A5E)), // mint
        Style(Color(0xFFFDF6E3), Color(0xFFF3E6C2), Color(0xFF0B6B52)), // cream
        Style(Color(0xFFEAF8F1), Color(0xFFD5F1E3), Color(0xFF0E6E68)), // mint
        Style(Color(0xFFFFFAEC), Color(0xFFF8EFD2), Color(0xFF8A5300)), // cream
    )
}
