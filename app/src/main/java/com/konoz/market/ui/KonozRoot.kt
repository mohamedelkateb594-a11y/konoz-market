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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.konoz.market.R
import com.konoz.market.data.entity.User
import com.konoz.market.domain.Permission
import com.konoz.market.domain.Permissions
import com.konoz.market.ui.cashier.CashierRoute
import com.konoz.market.ui.dashboard.DashboardActions
import com.konoz.market.ui.dashboard.DashboardRoute
import com.konoz.market.ui.more.MoreRoute
import com.konoz.market.ui.more.MoreTargets
import com.konoz.market.ui.products.ProductsRoute

/** [permission] null = every signed-in user sees the tab. */
private data class Tab(val id: Int, val labelRes: Int, val icon: ImageVector, val permission: Permission?)

private const val TAB_CASHIER = 0
private const val TAB_DASHBOARD = 1
private const val TAB_PRODUCTS = 2
private const val TAB_MORE = 3

/** Cashier is first, per the product requirement: the cashier must be at the front of the app. */
private val allTabs = listOf(
    Tab(TAB_CASHIER, R.string.nav_cashier, Icons.Filled.PointOfSale, Permission.SELL),
    Tab(TAB_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard, null),
    Tab(TAB_PRODUCTS, R.string.nav_products, Icons.Filled.Inventory2, Permission.MANAGE_PRODUCTS),
    Tab(TAB_MORE, R.string.nav_more, Icons.Filled.Menu, null),
)

private fun action(allowed: Boolean, block: () -> Unit): (() -> Unit)? = if (allowed) block else null

/** The app for the signed-in [user]: tabs, shortcuts and menu entries follow the user's role (see [Permissions]). */
@Composable
fun KonozRoot(user: User) {
    val role = user.role
    fun can(p: Permission) = Permissions.has(role, p)
    val tabs = remember(role) { allTabs.filter { it.permission == null || Permissions.has(role, it.permission) } }

    var savedTab by rememberSaveable { mutableIntStateOf(tabs.first().id) }
    val selected = if (tabs.any { it.id == savedTab }) savedTab else tabs.first().id
    var pendingBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var moreTarget by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab.id,
                        onClick = { savedTab = tab.id },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (selected) {
            TAB_CASHIER -> CashierRoute(
                onRegisterBarcode = { code ->
                    // Only roles that manage products can register an unknown barcode.
                    if (can(Permission.MANAGE_PRODUCTS)) { pendingBarcode = code; savedTab = TAB_PRODUCTS }
                },
                modifier = Modifier.padding(padding),
            )
            TAB_DASHBOARD -> DashboardRoute(
                actions = DashboardActions(
                    onCashier = action(can(Permission.SELL)) { savedTab = TAB_CASHIER },
                    onAddProduct = action(can(Permission.MANAGE_PRODUCTS)) { savedTab = TAB_PRODUCTS },
                    onStockCount = action(can(Permission.COUNT_STOCK)) { moreTarget = MoreTargets.COUNTS; savedTab = TAB_MORE },
                    onExpenses = action(can(Permission.MANAGE_EXPENSES)) { moreTarget = MoreTargets.EXPENSES; savedTab = TAB_MORE },
                    onReports = action(can(Permission.VIEW_REPORTS)) { moreTarget = MoreTargets.REPORTS; savedTab = TAB_MORE },
                ),
                showProfit = can(Permission.VIEW_PROFITS),
                modifier = Modifier.padding(padding),
            )
            TAB_PRODUCTS -> ProductsRoute(
                modifier = Modifier.padding(padding),
                newProductBarcode = pendingBarcode,
                onBarcodeConsumed = { pendingBarcode = null },
            )
            else -> MoreRoute(
                modifier = Modifier.padding(padding),
                startRoute = moreTarget,
                onStartConsumed = { moreTarget = null },
            )
        }
    }
}
