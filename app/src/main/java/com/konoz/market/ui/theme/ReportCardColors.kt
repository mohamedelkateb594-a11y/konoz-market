package com.konoz.market.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens of the report and home cards (Mint & Cream).
 * They follow the theme: light mode = mint and cream gradients with dark green text; dark mode = deep green gradients
 * with light text. Every description color keeps 4.5:1 or more on the end color of its gradient.
 * The getters are @Composable (they read the current theme), and every call site is already inside a composable.
 */
object ReportCardTokens {
    /** Card name, drawn bold. */
    val Title: Color @Composable get() = MaterialTheme.colorScheme.onSurface

    /** Short description. */
    val Description: Color
        @Composable get() = if (isDarkScheme()) Brand.TextSecondaryDark else Color(0xFF3F5A4B)

    /** Default icon color. */
    val IconDefault: Color
        @Composable get() = if (isDarkScheme()) Brand.SecondaryDarkMode else Brand.PrimaryDeep

    /** Translucent badge behind the icon (an icon background, never behind text). */
    val IconBadge: Color
        @Composable get() = if (isDarkScheme()) Color(0x33FFFFFF) else Color(0xB3FFFFFF)

    class Style(val gradientStart: Color, val gradientEnd: Color, val icon: Color)

    val styles: List<Style>
        @Composable get() = if (isDarkScheme()) {
            val money = Style(Brand.SurfaceVariantDark, Brand.CardMoneyDarkEnd, Brand.SecondaryDarkMode)
            val stock = Style(Brand.CardWarmDark, Brand.CardWarmDarkEnd, Color(0xFFE8D28A))
            val info = Style(Brand.CardInfoDark, Brand.CardInfoDarkEnd, Brand.PrimaryDarkMode)
            val alert = Style(Brand.CardAlertDark, Brand.CardAlertDarkEnd, Brand.WarningDarkMode)
            listOf(money, stock, info, money, alert, info, stock, money, info)
        } else {
            val money = Style(Brand.Accent, Brand.Secondary, Brand.PrimaryDeep)
            val stock = Style(Brand.Cream, Brand.CreamDeep, Color(0xFF8A6A12))
            val info = Style(Brand.MintSoft, Brand.Accent, Color(0xFF2B7A5E))
            val alert = Style(Brand.CardAlertLight, Brand.CardAlertLightEnd, Color(0xFF9A5B12))
            listOf(money, stock, info, money, alert, info, stock, money, info)
        }
}
