package com.konoz.market.ui.cashier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.KonozApp
import com.konoz.market.ui.receipt.InvoiceShareDialog
import com.konoz.market.ui.receipt.InvoicesViewModel

@Composable
fun CashierRoute(onRegisterBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    // View models outlive a logout (they belong to the activity), and CashierSession reads the signed-in user once when it starts.
    // Keying by user id gives each signed-in user a fresh session, so a cashier never inherits the admin's price-override right.
    val app = LocalContext.current.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    val userKey = user?.id ?: 0L
    val vm: CashierViewModel = viewModel(key = "cashier_$userKey", factory = CashierViewModel.Factory)

    // "Send invoice" on the receipt dialog opens the same send/share dialog as the Invoices screen (WhatsApp to a typed number, PDF, image).
    val shareVm: InvoicesViewModel = viewModel(key = "cashier_share_$userKey", factory = InvoicesViewModel.Factory)
    val shareUi by shareVm.ui.collectAsStateWithLifecycle()

    CashierScreen(vm = vm, onRegisterBarcode = onRegisterBarcode, onShareInvoice = shareVm::openByNumber, modifier = modifier)
    InvoiceShareDialog(shareUi, shareVm)
}
