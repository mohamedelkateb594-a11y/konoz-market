package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design Tokens - the ONLY place raw colors are defined.
 * Screens must use MaterialTheme.colorScheme or KonozTheme_.colors, never Color(0x...), Color.White or Color.Black.
 *
 * Identity: Modern Blue (owner decision 2026-11-15). Light and dark values are tuned separately, the dark theme is not an inverted light one.
 * Contrast (WCAG 2.1, measured): every text pair is >= 4.5:1, every control border (outline) and graphic is >= 3:1 in both themes.
 */
object Brand {
    // ---- Brand blues (light theme) ----
    val Primary = Color(0xFF174EA6)
    val PrimaryDeep = Color(0xFF0F3A7D)
    val Secondary = Color(0xFF2D6AD6) // was 4285F4: white text on it was 3.6:1, now 5.1:1
    val Accent = Color(0xFFE8F0FE)

    // ---- Light surfaces and text ----
    val BackgroundLight = Color(0xFFF5F7FB)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextMain = Color(0xFF1F2937)
    val TextSecondary = Color(0xFF56657B) // was 64748B: 4.4:1 on the background, now 5.5:1
    val OutlineLight = Color(0xFF76859B) // control borders (text fields, outlined buttons): 3.5:1 on the background
    val OutlineVariantLight = Color(0xFFE5EAF2) // decorative dividers and hairlines only
    val OnBrand = Color(0xFFFFFFFF) // text/icons on the saturated light-theme colors

    // ---- Semantic (light): white text on success/error/info stays readable, dark text on warning ----
    val Success = Color(0xFF0C7A54) // was 0F8A5F: 4.4:1, now 5.4:1
    val Warning = Color(0xFFE9A23B)
    val Error = Color(0xFFC93C3C)
    val Info = Color(0xFF0E7490)
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
    val OutlineDark = Color(0xFF6B7C99) // control borders: 4.0:1 on the surface (was 33425E, 1.7:1)
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
    val InfoDarkMode = Color(0xFF67D4E8)
    val WarningContainerDark = Color(0xFF4A3510)
    val OnWarningContainerDark = Color(0xFFFFE2A8)
    val ErrorContainerDark = Color(0xFF5A1F1F)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

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

    // ---- Legacy names, kept so existing screens keep compiling. They now carry the blue identity. Prefer the names above. ----
    val PrimaryGreen = Primary
    val DarkGreen = PrimaryDeep
    val FreshLime = Accent
    val WarmYellow = Warning
    val DangerRed = Error
    val SuccessGreen = Success
}
