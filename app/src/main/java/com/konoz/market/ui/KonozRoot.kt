package com.konoz.market.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.KonozApp
import com.konoz.market.R
import com.konoz.market.domain.Permission
import com.konoz.market.domain.Permissions
import com.konoz.market.ui.auth.AuthStage
import com.konoz.market.ui.auth.AuthViewModel
import com.konoz.market.ui.auth.ForcePinScreen
import com.konoz.market.ui.auth.LoginScreen
import com.konoz.market.ui.cashier.CashierRoute
import com.konoz.market.ui.dashboard.DashboardActions
import com.konoz.market.ui.dashboard.DashboardRoute
import com.konoz.market.ui.more.MoreRoute
import com.konoz.market.ui.more.MoreTargets
import com.konoz.market.ui.products.ProductsRoute

private enum class TabId { CASHIER, DASHBOARD, PRODUCTS, MORE }

private data class Tab(val id: TabId, val labelRes: Int, val icon: ImageVector)

private val allTabs = listOf(
    Tab(TabId.CASHIER, R.string.nav_cashier, Icons.Filled.PointOfSale),
    Tab(TabId.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard),
    Tab(TabId.PRODUCTS, R.string.nav_products, Icons.Filled.Inventory2),
    Tab(TabId.MORE, R.string.nav_more, Icons.Filled.Menu),
)

/** Login first; the temporary admin PIN must be replaced; then the app. */
@Composable
fun KonozRoot() {
    val auth: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val stage by auth.stage.collectAsStateWithLifecycle()
    when (stage) {
        AuthStage.SIGNED_OUT -> LoginScreen(auth)
        AuthStage.MUST_CHANGE_PIN -> ForcePinScreen(auth)
        AuthStage.READY -> MainApp()
    }
}

/** Cashier is first, per the product requirement. The dashboard (profit, costs, inventory value) is only for users who may see profit. */
@Composable
private fun MainApp() {
    val app = LocalContext.current.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    val canSeeProfit = user?.let { Permissions.has(it.role, Permission.VIEW_PROFIT) } == true
    val tabs = remember(canSeeProfit) { allTabs.filter { it.id != TabId.DASHBOARD || canSeeProfit } }

    var selectedName by rememberSaveable { mutableStateOf(TabId.CASHIER.name) }
    var pendingBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var moreTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = tabs.firstOrNull { it.id.name == selectedName }?.id ?: TabId.CASHIER
    fun select(id: TabId) {
        selectedName = id.name
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab.id,
                        onClick = { select(tab.id) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (selected) {
            TabId.CASHIER -> CashierRoute(
                onRegisterBarcode = { code -> pendingBarcode = code; select(TabId.PRODUCTS) },
                modifier = Modifier.padding(padding),
            )
            TabId.DASHBOARD -> DashboardRoute(
                actions = DashboardActions(
                    onCashier = { select(TabId.CASHIER) },
                    onAddProduct = { select(TabId.PRODUCTS) },
                    onStockCount = { moreTarget = MoreTargets.COUNTS; select(TabId.MORE) },
                    onExpenses = { moreTarget = MoreTargets.EXPENSES; select(TabId.MORE) },
                    onReports = { moreTarget = MoreTargets.REPORTS; select(TabId.MORE) },
                ),
                modifier = Modifier.padding(padding),
            )
            TabId.PRODUCTS -> ProductsRoute(
                modifier = Modifier.padding(padding),
                newProductBarcode = pendingBarcode,
                onBarcodeConsumed = { pendingBarcode = null },
            )
            TabId.MORE -> MoreRoute(
                modifier = Modifier.padding(padding),
                startRoute = moreTarget,
                onStartConsumed = { moreTarget = null },
            )
        }
    }
}
