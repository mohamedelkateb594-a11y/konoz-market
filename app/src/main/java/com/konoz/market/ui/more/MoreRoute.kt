package com.konoz.market.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.R
import com.konoz.market.domain.Permission
import com.konoz.market.domain.Permissions
import com.konoz.market.ui.alerts.StockAlertsScreen
import com.konoz.market.ui.alerts.StockAlertsViewModel
import com.konoz.market.ui.auth.ChangePinScreen
import com.konoz.market.ui.auth.ChangePinViewModel
import com.konoz.market.ui.auth.currentUser
import com.konoz.market.ui.auth.roleLabelRes
import com.konoz.market.ui.auth.signOutAndRestart
import com.konoz.market.ui.expenses.ExpensesScreen
import com.konoz.market.ui.expenses.ExpensesViewModel
import com.konoz.market.ui.movements.MovementLogScreen
import com.konoz.market.ui.movements.MovementLogViewModel
import com.konoz.market.ui.purchases.PurchaseScreen
import com.konoz.market.ui.purchases.PurchaseViewModel
import com.konoz.market.ui.reports.ReportsScreen
import com.konoz.market.ui.reports.ReportsViewModel
import com.konoz.market.ui.returns.ReturnsScreen
import com.konoz.market.ui.returns.ReturnsViewModel
import com.konoz.market.ui.shifts.ShiftScreen
import com.konoz.market.ui.shifts.ShiftViewModel
import com.konoz.market.ui.stockcount.StockCountListScreen
import com.konoz.market.ui.stockcount.StockCountListViewModel
import com.konoz.market.ui.stockcount.StockCountScreen
import com.konoz.market.ui.stockcount.StockCountViewModel
import com.konoz.market.ui.stockops.StockOpsScreen
import com.konoz.market.ui.stockops.StockOpsViewModel
import com.konoz.market.ui.suppliers.StatementScreen
import com.konoz.market.ui.suppliers.StatementViewModel
import com.konoz.market.ui.suppliers.SuppliersScreen
import com.konoz.market.ui.suppliers.SuppliersViewModel

/** Routes other screens (the dashboard shortcuts) may ask the More tab to open. */
object MoreTargets {
    const val EXPENSES = "expenses"
    const val COUNTS = "counts"
    const val REPORTS = "reports"
    const val SHIFTS = "shifts"
}

private const val MENU = "menu"
private const val SUPPLIERS = "suppliers"
private const val RETURNS = "returns"
private const val STOCK_OPS = "stockops"
private const val MOVEMENTS = "movements"
private const val ALERTS = "alerts"
private const val PIN = "pin"

/**
 * Routes: menu, suppliers, statement/{supplierId}, purchase/{supplierId}/{0|1 = return}, returns, stockops,
 * movements, alerts, expenses, counts, count/{countId}, reports, shifts, pin.
 */
private fun parentOf(route: String): String {
    val parts = route.split('/')
    return when (parts[0]) {
        "purchase" -> "statement/${parts[1]}"
        "statement" -> SUPPLIERS
        "count" -> MoreTargets.COUNTS
        else -> MENU
    }
}

/** [startRoute] (one of [MoreTargets]) opens that screen once, then [onStartConsumed] is called. */
@Composable
fun MoreRoute(modifier: Modifier = Modifier, startRoute: String? = null, onStartConsumed: () -> Unit = {}) {
    var route by rememberSaveable { mutableStateOf(MENU) }
    var session by rememberSaveable { mutableIntStateOf(0) } // new key per opened form so a screen never shows old input
    BackHandler(enabled = route != MENU) { route = parentOf(route) }
    LaunchedEffect(startRoute) {
        if (startRoute != null) {
            session++
            route = startRoute
            onStartConsumed()
        }
    }
    val context = LocalContext.current
    val user = currentUser()

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
        RETURNS -> {
            val vm: ReturnsViewModel = viewModel(key = "returns_$session", factory = ReturnsViewModel.Factory)
            ReturnsScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        STOCK_OPS -> {
            val vm: StockOpsViewModel = viewModel(key = "stockops_$session", factory = StockOpsViewModel.Factory)
            StockOpsScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        MOVEMENTS -> {
            val vm: MovementLogViewModel = viewModel(key = "movements_$session", factory = MovementLogViewModel.Factory)
            MovementLogScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        ALERTS -> {
            val vm: StockAlertsViewModel = viewModel(key = "alerts_$session", factory = StockAlertsViewModel.Factory)
            StockAlertsScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        MoreTargets.EXPENSES -> {
            val vm: ExpensesViewModel = viewModel(key = "expenses_$session", factory = ExpensesViewModel.Factory)
            ExpensesScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        MoreTargets.COUNTS -> {
            val vm: StockCountListViewModel = viewModel(key = "counts_$session", factory = StockCountListViewModel.Factory)
            StockCountListScreen(vm, onOpen = { route = "count/$it" }, onBack = { route = MENU }, modifier = modifier)
        }
        "count" -> {
            val countId = parts[1].toLong()
            val vm: StockCountViewModel = viewModel(key = "count_$countId", factory = StockCountViewModel.factory(countId))
            StockCountScreen(vm, onBack = { route = MoreTargets.COUNTS }, modifier = modifier)
        }
        MoreTargets.REPORTS -> {
            val vm: ReportsViewModel = viewModel(key = "reports_$session", factory = ReportsViewModel.Factory)
            ReportsScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        MoreTargets.SHIFTS -> {
            val vm: ShiftViewModel = viewModel(key = "shifts_$session", factory = ShiftViewModel.Factory)
            ShiftScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        PIN -> {
            val vm: ChangePinViewModel = viewModel(key = "pin_$session", factory = ChangePinViewModel.Factory)
            ChangePinScreen(vm, forced = false, onDone = { route = MENU }, onBack = { route = MENU }, modifier = modifier)
        }
        else -> {
            val role = user?.role
            fun can(p: Permission) = role != null && Permissions.has(role, p)
            val items = buildList<Pair<Int, () -> Unit>> {
                if (can(Permission.MANAGE_SHIFTS)) add(R.string.menu_shifts to { session++; route = MoreTargets.SHIFTS })
                if (can(Permission.VIEW_REPORTS)) add(R.string.menu_reports to { session++; route = MoreTargets.REPORTS })
                if (can(Permission.MANAGE_PURCHASES)) add(R.string.menu_suppliers to { route = SUPPLIERS })
                if (can(Permission.MANAGE_EXPENSES)) add(R.string.menu_expenses to { session++; route = MoreTargets.EXPENSES })
                if (can(Permission.COUNT_STOCK)) add(R.string.menu_stock_count to { session++; route = MoreTargets.COUNTS })
                if (can(Permission.SALES_RETURNS)) add(R.string.menu_returns to { session++; route = RETURNS })
                if (can(Permission.ADJUST_STOCK)) add(R.string.menu_stock_ops to { session++; route = STOCK_OPS })
                if (can(Permission.VIEW_INVENTORY)) add(R.string.menu_movements to { session++; route = MOVEMENTS })
                if (can(Permission.VIEW_INVENTORY)) add(R.string.menu_alerts to { session++; route = ALERTS })
                add(R.string.auth_menu_change_pin to { session++; route = PIN })
                add(R.string.auth_menu_logout to { signOutAndRestart(context) })
            }
            MoreMenu(
                subtitle = user?.let { stringResource(R.string.auth_signed_in_fmt, it.name, stringResource(roleLabelRes(it.role))) },
                items = items,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MoreMenu(subtitle: String?, items: List<Pair<Int, () -> Unit>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { (label, action) ->
            Card(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
