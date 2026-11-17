package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens of the dashboard cards (the strip of items on the home screen).
 * The cards are light-blue surfaces in both light and dark mode, so their text and icon colors are fixed on purpose
 * (dark navy on light blue); they do not follow the dark color scheme.
 */
object ReportCardTokens {
    /** Card name: dark navy, drawn bold. */
    val Title = Color(0xFF26364A)

    /** Short description: blue-gray. */
    val Description = Color(0xFF52647A)

    /** Default icon color, also the outline of the selected card. */
    val IconDefault = Color(0xFF245BDB)

    /** Translucent badge behind the icon (an icon background, never behind text). */
    val IconBadge = Color(0xB3FFFFFF)

    /** Light gradient (start to end) and a matching icon color. Every end color keeps the description text above 4.5:1 contrast. */
    class Style(val gradientStart: Color, val gradientEnd: Color, val icon: Color)

    val styles: List<Style> = listOf(
        Style(Color(0xFFEAF2FF), Color(0xFFD2E3FF), IconDefault),
        Style(Color(0xFFE6F3FF), Color(0xFFCBE6FF), Color(0xFF1565C0)),
        Style(Color(0xFFEBEFFF), Color(0xFFD4DCFF), Color(0xFF3949C0)),
        Style(Color(0xFFE3F6FB), Color(0xFFC7EBF7), Color(0xFF0B6E99)),
        Style(Color(0xFFECF4FF), Color(0xFFD6E8FF), IconDefault),
        Style(Color(0xFFE8F0FF), Color(0xFFD1E0FF), Color(0xFF1E4FB8)),
        Style(Color(0xFFE8F5FF), Color(0xFFCDE8FF), Color(0xFF0277BD)),
        Style(Color(0xFFEDF1FF), Color(0xFFD9E1FF), Color(0xFF2F54C8)),
        Style(Color(0xFFE6F3FE), Color(0xFFCCE5FB), Color(0xFF1A6AC4)),
    )
}
