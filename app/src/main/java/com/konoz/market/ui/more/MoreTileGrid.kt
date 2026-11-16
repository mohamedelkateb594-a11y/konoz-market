package com.konoz.market.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konoz.market.R

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

@Composable
private fun TileCard(tile: MoreTile) {
    val danger = tile.labelRes == R.string.auth_logout
    val badge = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val glyph = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Card(onClick = tile.onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(48.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                Icon(iconFor(tile.labelRes), contentDescription = null, tint = glyph, modifier = Modifier.size(26.dp))
            }
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
    R.string.menu_invoices -> Icons.Filled.Receipt
    R.string.menu_shifts -> Icons.Filled.Schedule
    R.string.menu_reports -> Icons.Filled.BarChart
    R.string.menu_suppliers -> Icons.Filled.LocalShipping
    R.string.menu_expenses -> Icons.Filled.Payments
    R.string.menu_stock_count -> Icons.Filled.FactCheck
    R.string.menu_returns -> Icons.Filled.AssignmentReturn
    R.string.menu_stock_ops -> Icons.Filled.Tune
    R.string.menu_movements -> Icons.Filled.SwapVert
    R.string.menu_alerts -> Icons.Filled.NotificationsActive
    R.string.menu_users -> Icons.Filled.Group
    R.string.menu_backup -> Icons.Filled.Backup
    R.string.auth_logout -> Icons.AutoMirrored.Filled.ExitToApp
    else -> Icons.Filled.Settings
}
