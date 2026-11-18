package com.konoz.market.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.Alignment
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
import com.konoz.market.ui.theme.ThemeMode
import com.konoz.market.ui.theme.ThemePreference

private const val SCREEN_HOME = "home"
private const val SCREEN_CASHIER = "cashier"
private const val SCREEN_PAGE = "page"

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

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.menu_theme_system
    ThemeMode.LIGHT -> R.string.menu_theme_light
    ThemeMode.DARK -> R.string.menu_theme_dark
}

/**
 * Three screens, no bottom bar:
 *  - home: the header, the swipeable cards (products and every former More item), and under them the details of the
 *    selected card (the dashboard when nothing is selected). The program settings are in the side menu.
 *  - cashier: opened only by the start-cashier button of the home screen; back returns to the home screen. It has a button
 *    that opens the shifts (the daily cash session) as a separate page and returns to the cashier.
 *  - page: a separate full screen for one program setting (users, shop data, backup, printer) or for the shifts opened from
 *    the cashier. Back returns to where it was opened from (home or cashier).
 * What each item needs is unchanged: the dashboard needs VIEW_PROFIT, products need EDIT_PRICES (so only the admin changes prices),
 * and MoreRoute still checks the permission of every route.
 */
@Composable
private fun MainApp() {
    val context = LocalContext.current
    val app = context.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    val themeMode by ThemePreference.mode
    fun allowed(permission: Permission?) = permission == null || user?.let { Permissions.has(it.role, permission) } == true
    val canSeeProfit = allowed(Permission.VIEW_PROFIT)
    val canEditProducts = allowed(Permission.EDIT_PRICES)
    val canOperateShifts = allowed(Permission.OPERATE_SHIFTS)

    var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var moreTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // The separate page: which More route it shows, and which screen (home or cashier) back returns to.
    var pageRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var pageReturn by rememberSaveable { mutableStateOf(SCREEN_HOME) }

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

    /** Opens a More route as its own full screen; back returns to [returnTo]. */
    fun openPage(route: String, returnTo: String) {
        pageRoute = route
        pageReturn = returnTo
        screen = SCREEN_PAGE
        menuOpen = false
    }

    BackHandler(enabled = screen == SCREEN_CASHIER) { screen = SCREEN_HOME }
    BackHandler(enabled = screen == SCREEN_HOME && menuOpen) { menuOpen = false }
    BackHandler(enabled = screen == SCREEN_HOME && !menuOpen && shownId != null) { selectedId = null }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            when (screen) {
                SCREEN_CASHIER -> Box(Modifier.fillMaxSize().padding(padding)) {
                    CashierRoute(
                        // Registering an unknown barcode creates a product with prices: only users who may edit products can do it.
                        onRegisterBarcode = { code -> if (canEditProducts) { pendingBarcode = code; open(HomeCatalog.PRODUCTS) } },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { screen = SCREEN_HOME },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    contentDescription = stringResource(R.string.home_back_to_home),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        // Opens the shifts (open or close the daily session, cash count) without leaving the cashier for good.
                        if (canOperateShifts) {
                            Surface(
                                onClick = { openPage(MoreTargets.SHIFTS, SCREEN_CASHIER) },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(width = 112.dp, height = 40.dp),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                ) {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(stringResource(R.string.menu_shifts), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                SCREEN_PAGE -> {
                    // Closing the page (its back arrow or the phone's back button) lands here through onHome and returns to the opener.
                    MoreRoute(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        startRoute = pageRoute,
                        onStartConsumed = { pageRoute = null },
                        embedded = true,
                        onHome = { screen = pageReturn },
                    )
                }
                else -> HomeScreen(
                    cards = stripItems,
                    selectedId = shownId,
                    onSelect = ::select,
                    onStartCashier = { screen = SCREEN_CASHIER },
                    onOpenMenu = { menuOpen = true },
                    modifier = Modifier.padding(padding),
                ) {
                    when {
                        shownId == null -> if (canSeeProfit) {
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
                        shownId == HomeCatalog.PRODUCTS -> ProductsRoute(
                            modifier = Modifier.fillMaxSize(),
                            newProductBarcode = pendingBarcode,
                            onBarcodeConsumed = { pendingBarcode = null },
                        )
                        else -> MoreRoute(
                            modifier = Modifier.fillMaxSize(),
                            startRoute = moreTarget,
                            onStartConsumed = { moreTarget = null },
                            embedded = true,
                            onHome = { selectedId = null },
                        )
                    }
                }
            }
        }
        if (screen == SCREEN_HOME) {
            HomeSideMenu(
                open = menuOpen,
                entries = menuItems,
                selectedId = null,
                // A program setting opens as its own screen, away from the home screen.
                onSelect = { item -> openPage(item.moreRoute ?: item.id, SCREEN_HOME) },
                onLanguage = { menuOpen = false; AppLanguage.switch(context) },
                onLogout = { menuOpen = false; app.session.signOut() },
                onClose = { menuOpen = false },
                // The appearance switch lived in the old More menu: it stays reachable from the side menu.
                themeLabelRes = themeLabel(themeMode),
                onTheme = { ThemePreference.next(context) },
            )
        }
    }
}
