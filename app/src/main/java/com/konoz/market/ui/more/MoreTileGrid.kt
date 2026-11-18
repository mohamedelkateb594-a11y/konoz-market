package com.konoz.market.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AssignmentReturn
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konoz.market.R
import com.konoz.market.ui.common.KonozIconBadge
import com.konoz.market.ui.theme.CardKind
import com.konoz.market.ui.theme.cardBrush

/** One tile of the More screen: its label and what happens on tap. Visibility by permission is decided by the caller. */
internal data class MoreTile(val labelRes: Int, val onClick: () -> Unit)

/** The More screen as a grid of tiles (two columns on a phone, more on a tablet; mirrors automatically in Arabic). */
@Composable
internal fun MoreTileGrid(tiles: List<MoreTile>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.more_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(tiles, key = { it.labelRes }) { tile -> TileCard(tile) }
        }
    }
}

/** Tile gradient by kind of data: money menus mint, stock menus cream, everything else pale mint. */
private fun kindFor(labelRes: Int): CardKind = when (labelRes) {
    R.string.menu_invoices, R.string.menu_shifts, R.string.menu_reports, R.string.menu_suppliers, R.string.menu_expenses -> CardKind.MONEY
    R.string.menu_stock_count, R.string.menu_returns, R.string.menu_stock_ops, R.string.menu_movements -> CardKind.STOCK
    R.string.menu_alerts -> CardKind.ALERT
    else -> CardKind.INFO
}

@Composable
private fun TileCard(tile: MoreTile) {
    val danger = tile.labelRes == R.string.auth_logout
    val badge = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val glyph = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        onClick = tile.onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(cardBrush(if (danger) CardKind.NEUTRAL else kindFor(tile.labelRes))).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            KonozIconBadge(iconFor(tile.labelRes), size = 52.dp, container = badge, tint = glyph)
            Text(
                stringResource(tile.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Entries added later by other tasks (printer, language, shop settings) get the settings icon until they are listed here. */
private fun iconFor(labelRes: Int): ImageVector = when (labelRes) {
    R.string.menu_invoices -> Icons.Rounded.Receipt
    R.string.menu_shifts -> Icons.Rounded.Schedule
    R.string.menu_reports -> Icons.Rounded.BarChart
    R.string.menu_suppliers -> Icons.Rounded.LocalShipping
    R.string.menu_expenses -> Icons.Rounded.Payments
    R.string.menu_stock_count -> Icons.Rounded.FactCheck
    R.string.menu_returns -> Icons.Rounded.AssignmentReturn
    R.string.menu_stock_ops -> Icons.Rounded.Tune
    R.string.menu_movements -> Icons.Rounded.SwapVert
    R.string.menu_alerts -> Icons.Rounded.NotificationsActive
    R.string.menu_users -> Icons.Rounded.Group
    R.string.menu_backup -> Icons.Rounded.Backup
    R.string.auth_logout -> Icons.AutoMirrored.Rounded.ExitToApp
    else -> Icons.Rounded.Settings
}
