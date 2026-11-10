package com.konoz.market.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes

@Composable
fun StockAlertsScreen(vm: StockAlertsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val low by vm.low.collectAsStateWithLifecycle()
    val out by vm.out.collectAsStateWithLifecycle()
    val expiring by vm.expiring.collectAsStateWithLifecycle()
    val expired by vm.expired.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.titleLarge)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabs = listOf(
                Triple(AlertTab.LOW, R.string.tab_low, low.size), Triple(AlertTab.OUT, R.string.tab_out, out.size),
                Triple(AlertTab.EXPIRING, R.string.tab_expiring, expiring.size), Triple(AlertTab.EXPIRED, R.string.tab_expired, expired.size),
            )
            items(tabs.size) { i ->
                val (t, label, count) = tabs[i]
                FilterChip(selected = tab == t, onClick = { vm.setTab(t) }, label = { Text(stringResource(label) + " (" + count + ")") })
            }
        }

        when (tab) {
            AlertTab.LOW -> ProductAlerts(low)
            AlertTab.OUT -> ProductAlerts(out)
            AlertTab.EXPIRING -> BatchAlerts(expiring, expiredList = false)
            AlertTab.EXPIRED -> BatchAlerts(expired, expiredList = true)
        }
    }
}

@Composable
private fun ProductAlerts(list: List<ProductAlert>) {
    if (list.isEmpty()) return Empty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.product.id }) { a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.product.name, style = MaterialTheme.typography.titleMedium)
                    val unit = stringResource(unitLabelRes(a.product.unit))
                    Text(stringResource(R.string.stock_fmt, formatQty(a.product.currentQtyMilli), unit), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.suggested_fmt, formatQty(a.suggestedMilli) + " " + unit), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun BatchAlerts(list: List<BatchAlert>, expiredList: Boolean) {
    if (list.isEmpty()) return Empty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.size) { i ->
            val a = list[i]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.productName, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.batch_qty_fmt, formatQty(a.qtyMilli)))
                    Text(
                        stringResource(if (expiredList) R.string.expired_since_fmt else R.string.expires_in_fmt, a.days),
                        color = if (expiredList) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Empty() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.no_alerts), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
