package com.konoz.market.ui.cashier

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.KonozApp
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.ui.receipt.InvoiceShareDialog
import com.konoz.market.ui.receipt.InvoicesViewModel

/** [onOpenShifts] is null when the signed-in user may not operate shifts: the shifts button is then not shown. */
@Composable
fun CashierRoute(
    onRegisterBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenShifts: (() -> Unit)? = null,
) {
    // View models outlive a logout (they belong to the activity), and CashierSession reads the signed-in user once when it starts.
    // Keying by user id gives each signed-in user a fresh session, so a cashier never inherits the admin's price-override right.
    val app = LocalContext.current.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    val userKey = user?.id ?: 0L
    val vm: CashierViewModel = viewModel(key = "cashier_$userKey", factory = CashierViewModel.Factory)

    // Right after a sale is paid, the same send dialog as the Invoices screen opens by itself: the cashier types the customer's
    // number and sends the invoice on WhatsApp (keyboard Send key or button), or taps Done to skip. Closing it ends the sale screen.
    val shareVm: InvoicesViewModel = viewModel(key = "cashier_share_$userKey", factory = InvoicesViewModel.Factory)
    val shareUi by shareVm.ui.collectAsStateWithLifecycle()
    val sale by vm.lastSale.collectAsStateWithLifecycle()

    LaunchedEffect(sale?.number) {
        sale?.let { shareVm.openByNumber(it.number.toString().toLong()) }
    }
    var shareWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(shareUi.selected) {
        if (shareUi.selected != null) {
            shareWasOpen = true
        } else if (shareWasOpen) {
            shareWasOpen = false
            vm.dismissSale()
        }
    }

    CashierScreen(vm = vm, onRegisterBarcode = onRegisterBarcode, onOpenShifts = onOpenShifts, modifier = modifier)
    InvoiceShareDialog(
        ui = shareUi,
        vm = shareVm,
        header = {
            Column {
                Text(stringResource(R.string.cashier_paid_ok), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                sale?.takeIf { it.changeDue > 0L }?.let {
                    Text(stringResource(R.string.receipt_change_fmt, Money.format(it.changeDue)), style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        focusPhone = true,
        closeLabelRes = R.string.done,
    )
}
