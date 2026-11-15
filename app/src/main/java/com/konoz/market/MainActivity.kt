package com.konoz.market

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.konoz.market.ui.KonozRoot
import com.konoz.market.ui.language.AppLanguage
import com.konoz.market.ui.theme.KonozTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.applyToApplication(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KonozTheme {
                KonozRoot()
            }
        }
    }
}
