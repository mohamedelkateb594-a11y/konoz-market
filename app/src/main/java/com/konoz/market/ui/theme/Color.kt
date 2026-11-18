package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design Tokens - the ONLY place raw colors are defined.
 * Screens must use MaterialTheme.colorScheme or KonozTheme_.colors, never Color(0x...), Color.White or Color.Black.
 *
 * Identity: Mint and Creamy (owner decision 2026-11-18, replaces Modern Blue). Light: a creamy background and warm-white surfaces,
 * deep mint for buttons and mint tints for containers, near-black green ink for text. Dark: deep green-black with mint accents.
 * Contrast (WCAG 2.1, guarded by ThemeContrastTest): every text pair is >= 4.5:1, every control border (outline) and graphic is >= 3:1.
 */
object Brand {
    // ---- Brand mints (light theme) ----
    val Primary = Color(0xFF0B6B52) // deep mint: white text on it 6.5:1
    val PrimaryDeep = Color(0xFF064E3B) // text on the mint tint 8:1
    val Secondary = Color(0xFF187A5E) // white text on it 5.3:1
    val Accent = Color(0xFFCFF0E0) // mint tint

    // ---- Light surfaces and text (creamy) ----
    val BackgroundLight = Color(0xFFFAF5E6) // cream
    val SurfaceLight = Color(0xFFFFFCF3) // warm white
    val SurfaceVariantLight = Color(0xFFE5F1EA) // pale mint: cards, pills, chips
    val ContainerLowestLight = Color(0xFFFFFEFA)
    val ContainerLowLight = Color(0xFFFFFAEC)
    val ContainerLight = Color(0xFFF5F0DF)
    val ContainerHighLight = Color(0xFFEDF1E2)
    val ContainerHighestLight = Color(0xFFE3F2EA) // default fill of Card
    val TextMain = Color(0xFF1B2A26) // green-black ink
    val TextSecondary = Color(0xFF4B5B55) // 6.6:1 on cream, 5.9:1 on the mint tint
    val OutlineLight = Color(0xFF6F8078) // control borders (text fields, outlined buttons): 3.8:1 on cream
    val OutlineVariantLight = Color(0xFFE4E6D6) // decorative dividers and hairlines only
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

    // ---- Dark theme ----
    val BackgroundDark = Color(0xFF0E1815)
    val SurfaceDark = Color(0xFF15221E)
    val SurfaceVariantDark = Color(0xFF1E2F29)
    val ContainerLowestDark = Color(0xFF0A120F)
    val ContainerLowDark = Color(0xFF111B17)
    val ContainerDark = Color(0xFF16241F)
    val ContainerHighDark = Color(0xFF1C2D27)
    val ContainerHighestDark = Color(0xFF223830)
    val TextMainDark = Color(0xFFE6F2EC)
    val TextSecondaryDark = Color(0xFFA9BDB4)
    val OutlineDark = Color(0xFF6F8A7F) // control borders: 4.4:1 on the surface
    val OutlineVariantDark = Color(0xFF263A33)
    val PrimaryDarkMode = Color(0xFF7FDDB8)
    val OnPrimaryDark = Color(0xFF04271B)
    val PrimaryContainerDark = Color(0xFF14503D)
    val SecondaryDarkMode = Color(0xFF5CCB9F)
    val OnSecondaryDark = Color(0xFF04271B)
    val SecondaryContainerDark = Color(0xFF1B3A2F)

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

    // ---- Legacy names, kept so existing screens keep compiling. They now carry the mint identity. Prefer the names above. ----
    val PrimaryGreen = Primary
    val DarkGreen = PrimaryDeep
    val FreshLime = Accent
    val WarmYellow = Warning
    val DangerRed = Error
    val SuccessGreen = Success
}
