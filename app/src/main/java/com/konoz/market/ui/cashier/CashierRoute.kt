package com.konoz.market.ui.cashier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.KonozApp

@Composable
fun CashierRoute(onRegisterBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    // View models outlive a logout (they belong to the activity), and CashierSession reads the signed-in user once when it starts.
    // Keying by user id gives each signed-in user a fresh session, so a cashier never inherits the admin's price-override right.
    val app = LocalContext.current.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    val vm: CashierViewModel = viewModel(key = "cashier_${user?.id ?: 0L}", factory = CashierViewModel.Factory)
    CashierScreen(vm = vm, onRegisterBarcode = onRegisterBarcode, modifier = modifier)
}
