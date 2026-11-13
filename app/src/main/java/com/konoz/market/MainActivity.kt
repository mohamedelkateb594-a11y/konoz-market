package com.konoz.market

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.konoz.market.ui.auth.AuthGate
import com.konoz.market.ui.theme.KonozTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KonozTheme {
                AuthGate()
            }
        }
    }
}
