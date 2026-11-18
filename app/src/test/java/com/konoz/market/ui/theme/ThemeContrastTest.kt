package com.konoz.market.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Guards the color tokens: text pairs must reach WCAG AA (4.5:1) and control borders 3:1, in light and in dark. */
class ThemeContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun text(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name: contrast %.2f is below 4.5".format(r), r >= 4.5)
    }

    private fun graphic(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name: contrast %.2f is below 3.0".format(r), r >= 3.0)
    }

    @Test fun light_text_pairs_reach_AA() {
        text("text on background", Brand.TextMain, Brand.BackgroundLight)
        text("text on surface", Brand.TextMain, Brand.SurfaceLight)
        text("text on surface variant", Brand.TextMain, Brand.SurfaceVariantLight)
        text("text on card fill", Brand.TextMain, Brand.ContainerHighestLight)
        text("secondary text on background", Brand.TextSecondary, Brand.BackgroundLight)
        text("secondary text on surface", Brand.TextSecondary, Brand.SurfaceLight)
        text("secondary text on surface variant", Brand.TextSecondary, Brand.SurfaceVariantLight)
        text("secondary text on card fill", Brand.TextSecondary, Brand.ContainerHighestLight)
        text("secondary text on accent", Brand.TextSecondary, Brand.Accent)
        text("on primary", Brand.OnBrand, Brand.Primary)
        text("on secondary", Brand.OnBrand, Brand.Secondary)
        text("primary container", Brand.PrimaryDeep, Brand.Accent)
        text("on error", Brand.OnBrand, Brand.Error)
        text("on success", Brand.OnBrand, Brand.Success)
        text("on info", Brand.OnBrand, Brand.Info)
        text("on warning", Brand.TextMain, Brand.Warning)
        text("warning container", Brand.OnWarningContainerLight, Brand.WarningContainerLight)
        text("error container", Brand.OnErrorContainerLight, Brand.ErrorContainerLight)
        text("success text on surface", Brand.Success, Brand.SurfaceLight)
        text("error text on surface", Brand.Error, Brand.SurfaceLight)
        text("sign-in button", Brand.OnBrand, Brand.SignInAccentLight)
        text("sign-in title on header", Brand.SignInAccentLight, Brand.SignInHeaderLight)
    }

    @Test fun dark_text_pairs_reach_AA() {
        text("text on background", Brand.TextMainDark, Brand.BackgroundDark)
        text("text on surface", Brand.TextMainDark, Brand.SurfaceDark)
        text("text on surface variant", Brand.TextMainDark, Brand.SurfaceVariantDark)
        text("text on card fill", Brand.TextMainDark, Brand.ContainerHighestDark)
        text("secondary text on background", Brand.TextSecondaryDark, Brand.BackgroundDark)
        text("secondary text on surface", Brand.TextSecondaryDark, Brand.SurfaceDark)
        text("secondary text on surface variant", Brand.TextSecondaryDark, Brand.SurfaceVariantDark)
        text("secondary text on card fill", Brand.TextSecondaryDark, Brand.ContainerHighestDark)
        text("on primary", Brand.OnPrimaryDark, Brand.PrimaryDarkMode)
        text("on secondary", Brand.OnSecondaryDark, Brand.SecondaryDarkMode)
        text("primary container", Brand.Accent, Brand.PrimaryContainerDark)
        text("secondary container", Brand.Accent, Brand.SecondaryContainerDark)
        text("on error", Brand.BackgroundDark, Brand.DangerDarkMode)
        text("on success", Brand.BackgroundDark, Brand.SuccessDarkMode)
        text("on info", Brand.BackgroundDark, Brand.InfoDarkMode)
        text("on warning", Brand.BackgroundDark, Brand.WarningDarkMode)
        text("warning container", Brand.OnWarningContainerDark, Brand.WarningContainerDark)
        text("error container", Brand.OnErrorContainerDark, Brand.ErrorContainerDark)
        text("success text on surface", Brand.SuccessDarkMode, Brand.SurfaceDark)
        text("error text on surface", Brand.DangerDarkMode, Brand.SurfaceDark)
        text("sign-in button", Brand.BackgroundDark, Brand.SignInAccentDark)
        text("sign-in title on background", Brand.SignInAccentDark, Brand.BackgroundDark)
    }

    @Test fun control_borders_are_visible_in_both_themes() {
        graphic("light outline on surface", Brand.OutlineLight, Brand.SurfaceLight)
        graphic("light outline on background", Brand.OutlineLight, Brand.BackgroundLight)
        graphic("dark outline on surface", Brand.OutlineDark, Brand.SurfaceDark)
        graphic("dark outline on background", Brand.OutlineDark, Brand.BackgroundDark)
        graphic("dark outline on surface variant", Brand.OutlineDark, Brand.SurfaceVariantDark)
    }

    /** The home cards are always light with dark navy text, whatever the app mode: every gradient end must keep both texts readable. */
    @Test fun home_cards_are_readable() {
        for ((i, s) in ReportCardTokens.styles.withIndex()) {
            text("card $i title on gradient end", ReportCardTokens.Title, s.gradientEnd)
            text("card $i description on gradient end", ReportCardTokens.Description, s.gradientEnd)
            text("card $i title on gradient start", ReportCardTokens.Title, s.gradientStart)
            text("card $i description on gradient start", ReportCardTokens.Description, s.gradientStart)
            graphic("card $i icon on gradient end", s.icon, s.gradientEnd)
        }
    }

    @Test fun backgrounds_are_never_pure_white_or_pure_black() {
        assertTrue(Brand.BackgroundLight != Color.White)
        assertTrue(Brand.BackgroundDark != Color.Black)
        assertTrue(luminance(Brand.BackgroundDark) > 0.0)
    }
}
