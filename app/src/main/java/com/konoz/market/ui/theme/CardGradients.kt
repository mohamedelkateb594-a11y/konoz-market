package com.konoz.market.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance

/** What a card shows decides its soft gradient. Text on every gradient is plain onSurface (above 7:1 in both modes). */
enum class CardKind {
    /** Plain information (names, codes). */
    NEUTRAL,

    /** Money: prices, profit, totals. Mint. */
    MONEY,

    /** Stock and quantities. Cream. */
    STOCK,

    /** Needs attention: low stock, expiry. Warm peach. */
    ALERT,

    /** Secondary info: barcodes, categories, suppliers. Pale mint. */
    INFO,
}

/** True when the current Material color scheme is the dark one (follows KonozTheme, not the system flag). */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The gradient of a card kind for the current theme: mint and cream in light mode, deep green and olive-cream in dark mode. */
@Composable
fun cardBrush(kind: CardKind): Brush {
    val dark = isDarkScheme()
    val stops = if (dark) {
        when (kind) {
            CardKind.NEUTRAL -> listOf(Brand.SurfaceDark, Brand.SurfaceVariantDark)
            CardKind.MONEY -> listOf(Brand.SurfaceVariantDark, Brand.CardMoneyDarkEnd)
            CardKind.STOCK -> listOf(Brand.CardWarmDark, Brand.CardWarmDarkEnd)
            CardKind.ALERT -> listOf(Brand.CardAlertDark, Brand.CardAlertDarkEnd)
            CardKind.INFO -> listOf(Brand.CardInfoDark, Brand.CardInfoDarkEnd)
        }
    } else {
        when (kind) {
            CardKind.NEUTRAL -> listOf(Brand.SurfaceLight, Brand.SurfaceContainerLight)
            CardKind.MONEY -> listOf(Brand.Accent, Brand.Secondary)
            CardKind.STOCK -> listOf(Brand.Cream, Brand.CreamDeep)
            CardKind.ALERT -> listOf(Brand.CardAlertLight, Brand.CardAlertLightEnd)
            CardKind.INFO -> listOf(Brand.MintSoft, Brand.Accent)
        }
    }
    return Brush.linearGradient(stops)
}
