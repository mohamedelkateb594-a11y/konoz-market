package com.konoz.market.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.konoz.market.ui.auth.AuthStage
import com.konoz.market.ui.auth.AuthViewModel
import com.konoz.market.ui.auth.ForcePinScreen
import com.konoz.market.ui.auth.LoginScreen
import com.konoz.market.ui.cashier.CashierRoute
import com.konoz.market.ui.dashboard.DashboardActions
import com.konoz.market.ui.dashboard.DashboardRoute
import com.konoz.market.ui.home.HomeCatalog
import com.konoz.market.ui.home.HomeHint
import com.konoz.market.ui.home.HomeItem
import com.konoz.market.ui.home.HomeScreen
import com.konoz.market.ui.home.HomeSideMenu
import com.konoz.market.ui.language.AppLanguage
import com.konoz.market.ui.more.MoreRoute
import com.konoz.market.ui.more.MoreTargets
import com.konoz.market.ui.products.ProductsRoute

private const val SCREEN_HOME = "home"
private const val SCREEN_CASHIER = "cashier"

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

/**
 * Two screens, no bottom bar:
 *  - home: the header, the swipeable cards (products and every former More item), and under them a sheet with the details of the
 *    selected card (the dashboard when nothing is selected). The program settings are in the side menu.
 *  - cashier: opened only by the start-cashier button of the home screen; its back button is a bar at the bottom.
 * What each item needs is unchanged: the dashboard needs VIEW_PROFIT, products need EDIT_PRICES (so only the admin changes prices),
 * and MoreRoute still checks the permission of every route.
 */
@Composable
private fun MainApp() {
    val context = LocalContext.current
    val app = context.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    fun allowed(permission: Permission?) = permission == null || user?.let { Permissions.has(it.role, permission) } == true
    val canSeeProfit = allowed(Permission.VIEW_PROFIT)
    val canEditProducts = allowed(Permission.EDIT_PRICES)

    var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var moreTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val stripItems = remember(user) { HomeCatalog.strip.filter { allowed(it.permission) } }
    val menuItems = remember(user) { HomeCatalog.menu.filter { allowed(it.permission) } }
    // A selection the signed-in user may not use (after switching users) shows the dashboard instead.
    val shownId = selectedId?.takeIf { id -> HomeCatalog.find(id)?.let { allowed(it.permission) } == true }

    fun open(id: String) {
        selectedId = id
        moreTarget = HomeCatalog.find(id)?.moreRoute
        screen = SCREEN_HOME
        menuOpen = false
    }
    fun select(item: HomeItem) = open(item.id)

    BackHandler(enabled = screen == SCREEN_CASHIER) { screen = SCREEN_HOME }
    BackHandler(enabled = screen == SCREEN_HOME && menuOpen) { menuOpen = false }
    BackHandler(enabled = screen == SCREEN_HOME && !menuOpen && shownId != null) { selectedId = null }

    // One explicit surface under everything, so the text color of every screen always matches the page behind it.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) { padding ->
                if (screen == SCREEN_CASHIER) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            CashierRoute(
                                // Registering an unknown barcode creates a product with prices: only users who may edit products can do it.
                                onRegisterBarcode = { code -> if (canEditProducts) { pendingBarcode = code; open(HomeCatalog.PRODUCTS) } },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // Back to the dashboard: at the bottom, never on the camera.
                        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                            FilledTonalButton(
                                onClick = { screen = SCREEN_HOME },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp),
                            ) {
                                Icon(Icons.Filled.Dashboard, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_back_to_home))
                            }
                        }
                    }
                } else {
                    HomeScreen(
                        cards = stripItems,
                        selectedId = shownId,
                        onSelect = ::select,
                        onStartCashier = { screen = SCREEN_CASHIER },
                        onOpenMenu = { menuOpen = true },
                        modifier = Modifier.padding(padding),
                    ) {
                        Crossfade(targetState = shownId, animationSpec = tween(220), label = "details") { id ->
                            when {
                                id == null -> if (canSeeProfit) {
                                    DashboardRoute(
                                        actions = DashboardActions(
                                            onCashier = { screen = SCREEN_CASHIER },
                                            onAddProduct = { open(HomeCatalog.PRODUCTS) },
                                            onStockCount = { open(MoreTargets.COUNTS) },
                                            onExpenses = { open(MoreTargets.EXPENSES) },
                                            onReports = { open(MoreTargets.REPORTS) },
                                        ),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    HomeHint()
                                }
                                id == HomeCatalog.PRODUCTS -> ProductsRoute(
                                    modifier = Modifier.fillMaxSize(),
                                    newProductBarcode = pendingBarcode,
                                    onBarcodeConsumed = { pendingBarcode = null },
                                )
                                else -> MoreRoute(
                                    modifier = Modifier.fillMaxSize(),
                                    // A screen that is fading out must not react to the new selection.
                                    startRoute = if (id == shownId) moreTarget else null,
                                    onStartConsumed = { moreTarget = null },
                                    embedded = true,
                                    onHome = { if (selectedId == id) selectedId = null },
                                )
                            }
                        }
                    }
                }
            }
            if (screen == SCREEN_HOME) {
                HomeSideMenu(
                    open = menuOpen,
                    entries = menuItems,
                    selectedId = shownId,
                    onSelect = ::select,
                    onLanguage = { menuOpen = false; AppLanguage.switch(context) },
                    onLogout = { menuOpen = false; app.session.signOut() },
                    onClose = { menuOpen = false },
                )
            }
        }
    }
}
