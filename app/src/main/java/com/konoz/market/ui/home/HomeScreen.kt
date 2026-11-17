package com.konoz.market.ui.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
 * and below it [content] (the details of the selected card, or the dashboard when nothing is selected).
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
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.home_menu), modifier = Modifier.size(28.dp))
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = onStartCashier,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            ) {
                Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_start_cashier), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        HomeStrip(cards, selectedId, onSelect)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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

@Composable
private fun HomeCard(
    item: HomeItem,
    style: ReportCardTokens.Style,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = style.gradientEnd),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 3.dp),
        border = if (selected) BorderStroke(2.dp, ReportCardTokens.IconDefault) else null,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(style.gradientStart, style.gradientEnd)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).background(ReportCardTokens.IconBadge, CircleShape), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = style.icon, modifier = Modifier.size(26.dp))
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

/** Shown under the strip when nothing is selected and the user has no dashboard (no profit permission). */
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
 * (the right in Arabic) and covers 75% of the screen width. Meant to be placed over the whole screen, outside the Scaffold.
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
            enter = slideInHorizontally(initialOffsetX = { if (rtl) it else -it }),
            exit = slideOutHorizontally(targetOffsetX = { if (rtl) it else -it }),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                shadowElevation = 12.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.home_menu_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    entries.forEach { item ->
                        MenuRow(stringResource(item.labelRes), item.icon, selected = item.id == selectedId, onClick = { onSelect(item) })
                    }
                    MenuRow(stringResource(R.string.menu_language), Icons.Filled.Language, selected = false, onClick = onLanguage)
                    MenuRow(stringResource(R.string.auth_logout), Icons.AutoMirrored.Filled.ExitToApp, selected = false, danger = true, onClick = onLogout)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
