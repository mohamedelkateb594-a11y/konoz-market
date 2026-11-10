package com.konoz.market.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.data.entity.MovementType
import com.konoz.market.ui.common.ProductSearch
import com.konoz.market.ui.common.formatQty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MovementLogScreen(vm: MovementLogViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val movements by vm.movements.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.movements_title), style = MaterialTheme.typography.titleLarge)
        }
        val product = selected
        if (product == null) {
            ProductSearch(query, results, stringResource(R.string.pick_product), vm::setQuery, { vm.select(it) }, Modifier.padding(horizontal = 16.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.select(null) }) { Text(stringResource(R.string.change_action)) }
            }
            if (movements.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_movements), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(movements, key = { it.id }) { m ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(stringResource(movementLabel(m.type)), style = MaterialTheme.typography.titleMedium)
                                    Text(fmt.format(Date(m.createdAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (m.unitCost > 0L) Text(stringResource(R.string.movement_cost_fmt, Money.format(m.unitCost)), style = MaterialTheme.typography.bodyMedium)
                                    m.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                }
                                Text(
                                    (if (m.qtyMilli > 0L) "+" else "") + formatQty(m.qtyMilli),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (m.qtyMilli < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun movementLabel(t: MovementType): Int = when (t) {
    MovementType.OPENING -> R.string.mv_opening
    MovementType.PURCHASE -> R.string.mv_purchase
    MovementType.SALE -> R.string.mv_sale
    MovementType.RETURN_IN -> R.string.mv_return_in
    MovementType.PURCHASE_RETURN -> R.string.mv_purchase_return
    MovementType.ADJUSTMENT -> R.string.mv_adjustment
    MovementType.DAMAGED -> R.string.mv_damaged
    MovementType.EXPIRED -> R.string.mv_expired
    MovementType.UNIT_CONVERSION -> R.string.mv_unit_conversion
    MovementType.COUNT_ADJUSTMENT -> R.string.mv_count_adjustment
}
