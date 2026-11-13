package com.konoz.market.ui.auth

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.KonozApp
import com.konoz.market.MainActivity
import com.konoz.market.R
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.launch

/** The signed-in user (null only while the sign-in screen is shown). */
@Composable
fun currentUser(): User? {
    val app = LocalContext.current.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    return user
}

@StringRes
fun roleLabelRes(role: UserRole): Int = when (role) {
    UserRole.ADMIN -> R.string.auth_role_admin
    UserRole.CASHIER -> R.string.auth_role_cashier
    UserRole.STOCK_KEEPER -> R.string.auth_role_stock_keeper
}

/**
 * Signs out and restarts the app on a fresh activity, so no screen or ViewModel of the previous user survives
 * (a ViewModel keeps the role it loaded; a new activity gets new ones).
 */
fun signOutAndRestart(context: Context) {
    val app = context.applicationContext as KonozApp
    val userId = app.session.user.value?.id
    app.session.signOut()
    if (userId != null) app.appScope.launch { app.authService.recordLogout(userId) }
    context.startActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}
