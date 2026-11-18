package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design Tokens - the ONLY place raw colors are defined.
 * Screens must use MaterialTheme.colorScheme or KonozTheme_.colors, never Color(0x...), Color.White or Color.Black.
 *
 * Identity: Mint & Cream (owner decision 2026-11-18, replaces Modern Blue). Light and dark values are tuned separately,
 * the dark theme is not an inverted light one.
 * Contrast (WCAG 2.1): TextMain on every mint or cream surface is above 7:1; control borders (outline) are >= 3:1 in both themes.
 * Known exception: the owner's primary #4D9870 with white text is about 3.5:1, so white on Primary is for bold button labels
 * and icons only; Primary used as TEXT on light surfaces is [PrimaryDeep] (about 6:1).
 */
object Brand {
    // ---- Brand greens (light theme) ----
    val Primary = Color(0xFF4D9870)
    val PrimaryDeep = Color(0xFF2F6F4F)
    /** The mint of the identity (#BDE3CA). Secondary buttons, selected chips and icon badges. Always paired with TextMain. */
    val Secondary = Color(0xFFBDE3CA)
    /** The light green of the identity (#E1F2E6). */
    val Accent = Color(0xFFE1F2E6)
    /** Cream used in card gradients (warm, never pure white). */
    val Cream = Color(0xFFFFF8E6)
    val CreamDeep = Color(0xFFF6EBCB)

    // ---- Light surfaces and text ----
    val BackgroundLight = Color(0xFFF7FAF5)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceContainerLight = Color(0xFFF1F7F1)
    val TextMain = Color(0xFF263B30)
    val TextSecondary = Color(0xFF647568)
    val OutlineLight = Color(0xFF7C9283) // control borders (text fields, outlined buttons): 3.2:1 on the background
    val OutlineVariantLight = Color(0xFFDDEADF) // decorative dividers and hairlines only
    val OnBrand = Color(0xFFFFFFFF) // text/icons on the saturated light-theme colors

    // ---- Semantic (light): white text on success/error/info stays readable, dark text on warning ----
    val Success = Color(0xFF0C7A54)
    val Warning = Color(0xFFE9A23B)
    val Error = Color(0xFFC93C3C)
    val Info = Color(0xFF0E7490)
    val WarningContainerLight = Color(0xFFFFF1D6)
    val OnWarningContainerLight = Color(0xFF5C3B00)
    val ErrorContainerLight = Color(0xFFFDE8E8)
    val OnErrorContainerLight = Color(0xFF7A1F1F)
    val SuccessContainerLight = Color(0xFFD7F2DE)
    val OnSuccessContainerLight = Color(0xFF0F4D22)

    // ---- Dark theme ----
    val BackgroundDark = Color(0xFF17231D)
    val SurfaceDark = Color(0xFF223229)
    val SurfaceVariantDark = Color(0xFF2B4033)
    val SurfaceContainerLowestDark = Color(0xFF131E18)
    val SurfaceContainerLowDark = Color(0xFF1B2A22)
    val TextMainDark = Color(0xFFF1F7F2)
    val TextSecondaryDark = Color(0xFFB5C7BA)
    val OutlineDark = Color(0xFF6F8A7B) // control borders: 3.5:1 on the surface
    val OutlineVariantDark = Color(0xFF35493D)
    /** Primary in dark mode (#83C69D). */
    val PrimaryDarkMode = Color(0xFF83C69D)
    val OnPrimaryDark = Color(0xFF0F2418)
    val PrimaryContainerDark = Color(0xFF355B45)
    /** The mint in dark mode (#9BD5B1). */
    val SecondaryDarkMode = Color(0xFF9BD5B1)
    val OnSecondaryDark = Color(0xFF12281C)
    val SecondaryContainerDark = Color(0xFF2B4033)

    // ---- Semantic (dark) ----
    val SuccessDarkMode = Color(0xFF6FD891)
    val WarningDarkMode = Color(0xFFF2B84B)
    val DangerDarkMode = Color(0xFFFF8F8F)
    val InfoDarkMode = Color(0xFF67D4E8)
    val WarningContainerDark = Color(0xFF4A3510)
    val OnWarningContainerDark = Color(0xFFFFE2A8)
    val ErrorContainerDark = Color(0xFF5A1F1F)
    val OnErrorContainerDark = Color(0xFFFFDAD6)
    val SuccessContainerDark = Color(0xFF1F4A2E)
    val OnSuccessContainerDark = Color(0xFFC8F2D4)

    // ---- Sign-in screen (the owner's reference design uses a violet accent; kept, but defined here and measured for contrast) ----
    val SignInAccentLight = Color(0xFF5850E6) // white text on it 5.7:1
    val SignInAccentDark = Color(0xFFA39EFF) // dark text on it 7.8:1
    val SignInHeaderLight = Color(0xFFE3E9F2)

    // ---- Neutrals used only by the sign-in illustration (a drawing, not UI text) ----
    val IllustrationWhite = Color(0xFFFFFFFF)
    val IllustrationGrey = Color(0xFFC9CDD6)
    val IllustrationGreyLight = Color(0xFFD6D9E0)
    val IllustrationFaint = Color(0xFFE6E9F0)
    val IllustrationLeaf = Color(0xFFF3F3F6)
    val IllustrationLeafLight = Color(0xFFF7F7F9)
    val IllustrationInk = Color(0xFF2B2D42)
    val IllustrationSkin = Color(0xFFF4B6B6)

    // ---- Card gradient stops (see CardGradients.kt). Light: mint and cream. Dark: deep green and olive-cream. ----
    val MintSoft = Color(0xFFEAF6ED)
    val CardWarmDark = Color(0xFF3A4430)
    val CardWarmDarkEnd = Color(0xFF46503A)
    val CardAlertLight = Color(0xFFFDEBD3)
    val CardAlertLightEnd = Color(0xFFF7D9B5)
    val CardAlertDark = Color(0xFF3D3224)
    val CardAlertDarkEnd = Color(0xFF4A3B27)
    val CardInfoDark = Color(0xFF244038)
    val CardInfoDarkEnd = Color(0xFF2B4B42)
    val CardMoneyDarkEnd = Color(0xFF34503F)

    // ---- Legacy names, kept so existing screens keep compiling. They now carry the mint identity. Prefer the names above. ----
    val PrimaryGreen = Primary
    val DarkGreen = PrimaryDeep
    val FreshLime = Accent
    val WarmYellow = Warning
    val DangerRed = Error
    val SuccessGreen = Success
}
