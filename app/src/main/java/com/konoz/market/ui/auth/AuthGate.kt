package com.konoz.market.ui.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.KonozApp
import com.konoz.market.ui.KonozRoot

/**
 * The app's front door: sign in, then (only for the seeded admin still on its temporary PIN) a forced PIN change,
 * then the real app. Nothing behind it is composed before the user is signed in.
 */
@Composable
fun AuthGate() {
    val app = LocalContext.current.applicationContext as KonozApp
    val authVm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val user by app.session.user.collectAsStateWithLifecycle()
    val force by authVm.forcePinChange.collectAsStateWithLifecycle()
    val signedIn = user

    Surface(Modifier.fillMaxSize()) {
        when {
            signedIn == null -> LoginScreen(authVm, Modifier.systemBarsPadding())
            force -> {
                val vm: ChangePinViewModel = viewModel(key = "pin_forced", factory = ChangePinViewModel.Factory)
                ChangePinScreen(vm, forced = true, onDone = authVm::pinChanged, onBack = null, modifier = Modifier.systemBarsPadding())
            }
            else -> KonozRoot(signedIn)
        }
    }
}
