package com.konoz.market.ui.cashier

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CashierRoute(onRegisterBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm: CashierViewModel = viewModel(factory = CashierViewModel.Factory)
    CashierScreen(vm = vm, onRegisterBarcode = onRegisterBarcode, modifier = modifier)
}
