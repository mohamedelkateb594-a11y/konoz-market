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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.KonozApp
import com.konoz.market.R
import com.konoz.market.domain.Permission
import com.konoz.market.domain.Permissions
import com.konoz.market.ui.alerts.StockAlertsScreen
import com.konoz.market.ui.alerts.StockAlertsViewModel
import com.konoz.market.ui.expenses.ExpensesScreen
import com.konoz.market.ui.expenses.ExpensesViewModel
import com.konoz.market.ui.movements.MovementLogScreen
import com.konoz.market.ui.movements.MovementLogViewModel
import com.konoz.market.ui.purchases.PurchaseScreen
import com.konoz.market.ui.purchases.PurchaseViewModel
import com.konoz.market.ui.receipt.InvoicesScreen
import com.konoz.market.ui.receipt.InvoicesViewModel
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
import com.konoz.market.ui.users.UsersScreen
import com.konoz.market.ui.users.UsersViewModel

/** Routes other screens (the dashboard shortcuts) may ask the More tab to open. */
object MoreTargets {
    const val EXPENSES = "expenses"
    const val COUNTS = "counts"
    const val REPORTS = "reports"
    const val SHIFTS = "shifts"
    const val USERS = "users"
    const val INVOICES = "invoices"
}

private const val MENU = "menu"
private const val SUPPLIERS = "suppliers"
private const val RETURNS = "returns"
private const val STOCK_OPS = "stockops"
private const val MOVEMENTS = "movements"
private const val ALERTS = "alerts"

/**
 * Routes: menu, suppliers, statement/{supplierId}, purchase/{supplierId}/{0|1 = return}, returns, stockops,
 * movements, alerts, expenses, counts, count/{countId}, reports, shifts, users, invoices.
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

/** The permission a route needs; null = any signed-in user (alerts, invoices). */
private fun requiredPermission(route: String): Permission? = when (route.split('/')[0]) {
    SUPPLIERS, "statement", "purchase" -> Permission.MANAGE_PURCHASES
    RETURNS -> Permission.PROCESS_RETURN
    STOCK_OPS, MOVEMENTS -> Permission.ADJUST_STOCK
    MoreTargets.EXPENSES -> Permission.MANAGE_EXPENSES
    MoreTargets.COUNTS, "count" -> Permission.COUNT_STOCK
    MoreTargets.REPORTS -> Permission.VIEW_PROFIT
    MoreTargets.SHIFTS -> Permission.OPERATE_SHIFTS
    MoreTargets.USERS -> Permission.MANAGE_USERS
    else -> null
}

/** [startRoute] (one of [MoreTargets]) opens that screen once, then [onStartConsumed] is called. */
@Composable
fun MoreRoute(modifier: Modifier = Modifier, startRoute: String? = null, onStartConsumed: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    fun allowed(permission: Permission?) = permission == null || user?.let { Permissions.has(it.role, permission) } == true

    var route by rememberSaveable { mutableStateOf(MENU) }
    var session by rememberSaveable { mutableIntStateOf(0) } // new key per opened form so a screen never shows old input
    // A route the signed-in user may not use (restored after switching users, or asked for by a shortcut) shows the menu instead.
    val shown = if (allowed(requiredPermission(route))) route else MENU
    BackHandler(enabled = shown != MENU) { route = parentOf(shown) }
    LaunchedEffect(startRoute) {
        if (startRoute != null) {
            session++
            route = startRoute
            onStartConsumed()
        }
    }

    val parts = shown.split('/')
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
        MoreTargets.USERS -> {
            val vm: UsersViewModel = viewModel(key = "users_$session", factory = UsersViewModel.Factory)
            UsersScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        MoreTargets.INVOICES -> {
            val vm: InvoicesViewModel = viewModel(key = "invoices_$session", factory = InvoicesViewModel.Factory)
            InvoicesScreen(vm, onBack = { route = MENU }, modifier = modifier)
        }
        else -> MoreMenu(
            items = listOf(
                Triple<Int, Permission?, () -> Unit>(R.string.menu_invoices, null) { session++; route = MoreTargets.INVOICES },
                Triple(R.string.menu_shifts, Permission.OPERATE_SHIFTS) { session++; route = MoreTargets.SHIFTS },
                Triple(R.string.menu_reports, Permission.VIEW_PROFIT) { session++; route = MoreTargets.REPORTS },
                Triple(R.string.menu_suppliers, Permission.MANAGE_PURCHASES) { route = SUPPLIERS },
                Triple(R.string.menu_expenses, Permission.MANAGE_EXPENSES) { session++; route = MoreTargets.EXPENSES },
                Triple(R.string.menu_stock_count, Permission.COUNT_STOCK) { session++; route = MoreTargets.COUNTS },
                Triple(R.string.menu_returns, Permission.PROCESS_RETURN) { session++; route = RETURNS },
                Triple(R.string.menu_stock_ops, Permission.ADJUST_STOCK) { session++; route = STOCK_OPS },
                Triple(R.string.menu_movements, Permission.ADJUST_STOCK) { session++; route = MOVEMENTS },
                Triple<Int, Permission?, () -> Unit>(R.string.menu_alerts, null) { session++; route = ALERTS },
                Triple(R.string.menu_users, Permission.MANAGE_USERS) { session++; route = MoreTargets.USERS },
                Triple<Int, Permission?, () -> Unit>(R.string.auth_logout, null) { route = MENU; app.session.signOut() },
            ).filter { allowed(it.second) },
            modifier = modifier,
        )
    }
}

@Composable
private fun MoreMenu(items: List<Triple<Int, Permission?, () -> Unit>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.titleLarge)
        items.forEach { (label, _, action) ->
            Card(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
