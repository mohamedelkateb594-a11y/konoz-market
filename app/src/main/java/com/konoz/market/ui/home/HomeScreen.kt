package com.konoz.market.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.konoz.market.R
import com.konoz.market.domain.Permission
import com.konoz.market.ui.more.MoreTargets
import com.konoz.market.ui.theme.ReportCardTokens
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * One entry of the home screen. [id] is also the route of the More screens (see MoreRoute) except for "products",
 * which is hosted by ProductsRoute. [moreRoute] = null means it is not a More screen.
 */
class HomeItem(
    val id: String,
    val labelRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val permission: Permission?,
    val moreRoute: String?,
)

/** What the home screen offers: [strip] = the swipeable cards, [menu] = the program settings in the side menu. */
object HomeCatalog {
    const val PRODUCTS = "products"

    val strip: List<HomeItem> = listOf(
        HomeItem(PRODUCTS, R.string.nav_products, R.string.hc_products_desc, Icons.Filled.Inventory2, Permission.EDIT_PRICES, null),
        HomeItem(MoreTargets.INVOICES, R.string.menu_invoices, R.string.hc_invoices_desc, Icons.Filled.Receipt, null, MoreTargets.INVOICES),
        HomeItem(MoreTargets.SHIFTS, R.string.menu_shifts, R.string.hc_shifts_desc, Icons.Filled.Schedule, Permission.OPERATE_SHIFTS, MoreTargets.SHIFTS),
        HomeItem(MoreTargets.REPORTS, R.string.menu_reports, R.string.hc_reports_desc, Icons.Filled.BarChart, Permission.VIEW_PROFIT, MoreTargets.REPORTS),
        HomeItem("suppliers", R.string.menu_suppliers, R.string.hc_suppliers_desc, Icons.Filled.LocalShipping, Permission.MANAGE_PURCHASES, "suppliers"),
        HomeItem(MoreTargets.EXPENSES, R.string.menu_expenses, R.string.hc_expenses_desc, Icons.Filled.Payments, Permission.MANAGE_EXPENSES, MoreTargets.EXPENSES),
        HomeItem(MoreTargets.COUNTS, R.string.menu_stock_count, R.string.hc_stockcount_desc, Icons.Filled.FactCheck, Permission.COUNT_STOCK, MoreTargets.COUNTS),
        HomeItem("returns", R.string.menu_returns, R.string.hc_returns_desc, Icons.Filled.AssignmentReturn, Permission.PROCESS_RETURN, "returns"),
        HomeItem("stockops", R.string.menu_stock_ops, R.string.hc_stockops_desc, Icons.Filled.Inventory2, Permission.ADJUST_STOCK, "stockops"),
        HomeItem("movements", R.string.menu_movements, R.string.hc_movements_desc, Icons.Filled.SwapVert, Permission.ADJUST_STOCK, "movements"),
        HomeItem("alerts", R.string.menu_alerts, R.string.hc_alerts_desc, Icons.Filled.NotificationsActive, null, "alerts"),
    )

    val menu: List<HomeItem> = listOf(
        HomeItem(MoreTargets.USERS, R.string.menu_users, R.string.hc_users_desc, Icons.Filled.Group, Permission.MANAGE_USERS, MoreTargets.USERS),
        HomeItem(MoreTargets.SHOP, R.string.shop_menu, R.string.hc_shop_desc, Icons.Filled.Storefront, Permission.MANAGE_USERS, MoreTargets.SHOP),
        HomeItem(MoreTargets.BACKUP, R.string.menu_backup, R.string.hc_backup_desc, Icons.Filled.Backup, Permission.MANAGE_BACKUP, MoreTargets.BACKUP),
        HomeItem(MoreTargets.PRINTER, R.string.menu_printer, R.string.hc_printer_desc, Icons.Filled.Print, null, MoreTargets.PRINTER),
    )

    fun find(id: String?): HomeItem? = if (id == null) null else (strip + menu).firstOrNull { it.id == id }
}

/**
 * The home screen: a header (side-menu button at the start, the start-cashier button at the end), the swipeable strip of cards,
 * and under it a sheet that rises from the bottom with [content] (the details of the selected card, or the dashboard).
 */
@Composable
fun HomeScreen(
    cards: List<HomeItem>,
    selectedId: String?,
    onSelect: (HomeItem) -> Unit,
    onStartCashier: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = onOpenMenu,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.home_menu),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = onStartCashier,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
            ) {
                Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_start_cashier), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        HomeStrip(cards, selectedId, onSelect)
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 10.dp,
        ) { content() }
    }
}

/**
 * Landscape cards in a horizontal strip. Swipe left or right; the strip snaps to a card, and the cards near the edges
 * shrink and fade a little, so moving between them is smooth.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeStrip(cards: List<HomeItem>, selectedId: String?, onSelect: (HomeItem) -> Unit) {
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val cardWidth = (LocalConfiguration.current.screenWidthDp * 0.66f).toInt().coerceIn(220, 280).dp

    LazyRow(
        state = listState,
        flingBehavior = fling,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards.size, key = { cards[it].id }) { index ->
            val item = cards[index]
            HomeCard(
                item = item,
                style = ReportCardTokens.styles[index % ReportCardTokens.styles.size],
                selected = item.id == selectedId,
                onClick = { onSelect(item) },
                modifier = Modifier
                    .width(cardWidth)
                    .height(96.dp)
                    .graphicsLayer {
                        val info = listState.layoutInfo
                        val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
                        val far = if (visible == null) {
                            1f
                        } else {
                            val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
                            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val itemCenter = visible.offset + visible.size / 2f
                            (abs(itemCenter - viewportCenter) / viewport).coerceIn(0f, 1f)
                        }
                        val scale = 1f - 0.06f * far
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.15f * far
                    },
            )
        }
    }
}

/** A card springs down while pressed, lifts and gets an outline when selected. */
@Composable
private fun HomeCard(
    item: HomeItem,
    style: ReportCardTokens.Style,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "press")
    val elevation by animateDpAsState(if (selected) 10.dp else 3.dp, label = "elevation")
    val outline by animateColorAsState(if (selected) ReportCardTokens.IconDefault else Color.Transparent, label = "outline")
    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = press
            scaleY = press
        },
        interactionSource = source,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = style.gradientEnd),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(2.dp, outline),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(style.gradientStart, style.gradientEnd)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(50.dp).background(ReportCardTokens.IconBadge, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = style.icon, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(item.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ReportCardTokens.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(item.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = ReportCardTokens.Description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Shown in the sheet when nothing is selected and the user has no dashboard (no profit permission). */
@Composable
fun HomeHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.home_pick_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The side menu with the program settings (users, shop data, backup, printer, language, sign-out). It opens from the start edge
 * (the right in Arabic) and covers 75% of the screen width; its rows come in one after the other. Meant to be placed over the
 * whole screen, outside the Scaffold.
 */
@Composable
fun HomeSideMenu(
    open: Boolean,
    entries: List<HomeItem>,
    selectedId: String?,
    onSelect: (HomeItem) -> Unit,
    onLanguage: () -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.75f),
            enter = slideInHorizontally(animationSpec = tween(320), initialOffsetX = { if (rtl) it else -it }),
            exit = slideOutHorizontally(animationSpec = tween(240), targetOffsetX = { if (rtl) it else -it }),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                shadowElevation = 16.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Staggered(0) { MenuHero() }
                    Spacer(Modifier.height(4.dp))
                    entries.forEachIndexed { i, item ->
                        Staggered(i + 1) {
                            MenuRow(
                                label = stringResource(item.labelRes),
                                description = stringResource(item.descRes),
                                icon = item.icon,
                                selected = item.id == selectedId,
                                onClick = { onSelect(item) },
                            )
                        }
                    }
                    Staggered(entries.size + 1) {
                        MenuRow(
                            label = stringResource(R.string.menu_language),
                            description = stringResource(R.string.hc_language_desc),
                            icon = Icons.Filled.Language,
                            selected = false,
                            onClick = onLanguage,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Staggered(entries.size + 2) {
                        MenuRow(
                            label = stringResource(R.string.auth_logout),
                            description = stringResource(R.string.hc_logout_desc),
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            selected = false,
                            danger = true,
                            onClick = onLogout,
                        )
                    }
                }
            }
        }
    }
}

/** Fades a row in and slides it from the menu's own edge, [index] x 70 ms after the menu opened. */
@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120L + 70L * index)
        shown = true
    }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "menu row")
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationX = (1f - progress) * 36.dp.toPx() * (if (rtl) 1f else -1f)
        },
    ) { content() }
}

@Composable
private fun MenuHero() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                RoundedCornerShape(26.dp),
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(54.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.home_menu_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = when {
            selected -> scheme.primaryContainer
            danger -> scheme.errorContainer
            else -> scheme.surfaceVariant
        },
        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).background(scheme.surface, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (danger) scheme.error else scheme.primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (danger) scheme.onErrorContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (danger) scheme.onErrorContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!danger) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
        }
    }
}
