package com.konoz.market.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.R
import com.konoz.market.ui.purchases.PurchaseScreen
import com.konoz.market.ui.purchases.PurchaseViewModel
import com.konoz.market.ui.suppliers.StatementScreen
import com.konoz.market.ui.suppliers.StatementViewModel
import com.konoz.market.ui.suppliers.SuppliersScreen
import com.konoz.market.ui.suppliers.SuppliersViewModel

private const val MENU = "menu"
private const val SUPPLIERS = "suppliers"

/** Routes: menu, suppliers, statement/{supplierId}, purchase/{supplierId}/{0|1 = return}. */
private fun parentOf(route: String): String {
    val parts = route.split('/')
    return when (parts[0]) {
        "purchase" -> "statement/${parts[1]}"
        "statement" -> SUPPLIERS
        else -> MENU
    }
}

@Composable
fun MoreRoute(modifier: Modifier = Modifier) {
    var route by rememberSaveable { mutableStateOf(MENU) }
    var session by rememberSaveable { mutableIntStateOf(0) } // new key per opened invoice so a form never shows old input
    BackHandler(enabled = route != MENU) { route = parentOf(route) }

    val parts = route.split('/')
    when (parts[0]) {
        SUPPLIERS -> {
            val vm: SuppliersViewModel = viewModel(factory = SuppliersViewModel.Factory)
            SuppliersScreen(vm, onOpen = { route = "statement/$it" }, onBack = { route = MENU }, modifier = modifier)
        }
        "statement" -> {
            val supplierId = parts[1].toLong()
            val vm: StatementViewModel = viewModel(key = "statement_$supplierId", factory = StatementViewModel.factory(supplierId))
            StatementScreen(
                vm = vm,
                onNewPurchase = { session++; route = "purchase/$supplierId/0" },
                onNewReturn = { session++; route = "purchase/$supplierId/1" },
                onBack = { route = SUPPLIERS },
                modifier = modifier,
            )
        }
        "purchase" -> {
            val supplierId = parts[1].toLong()
            val isReturn = parts[2] == "1"
            val vm: PurchaseViewModel = viewModel(
                key = "purchase_${supplierId}_${parts[2]}_$session",
                factory = PurchaseViewModel.factory(supplierId, isReturn),
            )
            PurchaseScreen(vm, onClose = { route = "statement/$supplierId" }, modifier = modifier)
        }
        else -> MoreMenu(onSuppliers = { route = SUPPLIERS }, modifier = modifier)
    }
}

@Composable
private fun MoreMenu(onSuppliers: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.titleLarge)
        Card(onClick = onSuppliers, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.menu_suppliers), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }
    }
}
