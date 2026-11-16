package com.konoz.market

import android.content.Context
import android.graphics.Color as PlatformColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import com.konoz.market.ui.KonozRoot
import com.konoz.market.ui.language.AppLanguage
import com.konoz.market.ui.theme.KonozTheme
import com.konoz.market.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.applyToApplication(applicationContext)
        super.onCreate(savedInstanceState)
        ThemePreference.load(this)
        setContent {
            val mode by ThemePreference.mode
            val dark = ThemePreference.isDark(mode, isSystemInDarkTheme())
            // Transparent bars; the icon color follows the app's own mode (not only the phone's), so the clock and battery stay readable.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(PlatformColor.TRANSPARENT, PlatformColor.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            KonozTheme(darkTheme = dark) {
                KonozRoot()
            }
        }
    }
}
