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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.konoz.market.R
import com.konoz.market.ui.cashier.CashierRoute
import com.konoz.market.ui.dashboard.DashboardActions
import com.konoz.market.ui.dashboard.DashboardRoute
import com.konoz.market.ui.more.MoreRoute
import com.konoz.market.ui.more.MoreTargets
import com.konoz.market.ui.products.ProductsRoute

private data class Tab(val labelRes: Int, val icon: ImageVector)

/** Cashier is first, per the product requirement: the cashier must be at the front of the app. */
private val tabs = listOf(
    Tab(R.string.nav_cashier, Icons.Filled.PointOfSale),
    Tab(R.string.nav_dashboard, Icons.Filled.Dashboard),
    Tab(R.string.nav_products, Icons.Filled.Inventory2),
    Tab(R.string.nav_more, Icons.Filled.Menu),
)

private const val TAB_CASHIER = 0
private const val TAB_DASHBOARD = 1
private const val TAB_PRODUCTS = 2
private const val TAB_MORE = 3

@Composable
fun KonozRoot() {
    var selected by rememberSaveable { mutableIntStateOf(TAB_CASHIER) }
    var pendingBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var moreTarget by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (selected) {
            TAB_CASHIER -> CashierRoute(
                onRegisterBarcode = { code -> pendingBarcode = code; selected = TAB_PRODUCTS },
                modifier = Modifier.padding(padding),
            )
            TAB_DASHBOARD -> DashboardRoute(
                actions = DashboardActions(
                    onCashier = { selected = TAB_CASHIER },
                    onAddProduct = { selected = TAB_PRODUCTS },
                    onStockCount = { moreTarget = MoreTargets.COUNTS; selected = TAB_MORE },
                    onExpenses = { moreTarget = MoreTargets.EXPENSES; selected = TAB_MORE },
                    onReports = { moreTarget = MoreTargets.REPORTS; selected = TAB_MORE },
                ),
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
