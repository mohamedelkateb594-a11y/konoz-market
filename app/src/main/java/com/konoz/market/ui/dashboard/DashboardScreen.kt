package com.konoz.market.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.domain.DayPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What the shortcut buttons do; the root decides how to navigate. A null action hides that shortcut (the user's role may not allow it). */
class DashboardActions(
    val onCashier: (() -> Unit)?,
    val onAddProduct: (() -> Unit)?,
    val onStockCount: (() -> Unit)?,
    val onExpenses: (() -> Unit)?,
    val onReports: (() -> Unit)?,
)

/** [showProfit] = false hides the estimated profit (only users who may view profits see it). */
@Composable
fun DashboardRoute(actions: DashboardActions, showProfit: Boolean, modifier: Modifier = Modifier) {
    // New key on a new day, so a dashboard left open overnight does not keep showing yesterday as "today".
    val dayKey = remember { Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) } }
    val vm: DashboardViewModel = viewModel(key = "dashboard_$dayKey", factory = DashboardViewModel.Factory)
    DashboardScreen(vm, actions, showProfit, modifier)
}

@Composable
fun DashboardScreen(vm: DashboardViewModel, actions: DashboardActions, showProfit: Boolean, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val today = ui.today
    val timeFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.dash_title), style = MaterialTheme.typography.titleLarge) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.dash_today_sales), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(today?.grossSales ?: 0L), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Kpi(stringResource(R.string.dash_invoices), (today?.invoiceCount ?: 0).toString(), Modifier.weight(1f))
                Kpi(stringResource(R.string.dash_net_sales), Money.format(today?.netSales ?: 0L), Modifier.weight(1f))
            }
        }
        item {
            val profit = today?.estimatedNetProfit ?: 0L
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Kpi(stringResource(R.string.dash_expenses), Money.format(today?.expenses ?: 0L), Modifier.weight(1f))
                if (showProfit) {
                    Kpi(
                        stringResource(R.string.dash_profit), Money.format(profit), Modifier.weight(1f),
                        valueColor = if (profit < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Kpi(stringResource(R.string.dash_inventory_value), Money.format(ui.inventoryValue), Modifier.weight(1f))
                Kpi(stringResource(R.string.dash_low_stock), ui.lowStockCount.toString(), Modifier.weight(1f))
            }
        }
        item { Kpi(stringResource(R.string.dash_expiring), ui.expiringBatches.toString(), Modifier.fillMaxWidth()) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dash_last7), style = MaterialTheme.typography.titleMedium)
                    WeekChart(ui.series)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.dash_recent), style = MaterialTheme.typography.titleMedium)
                    if (ui.recent.isEmpty()) {
                        Text(stringResource(R.string.dash_no_sales), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else ui.recent.forEach { inv ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.dash_invoice_fmt, inv.number ?: 0L) + "  -  " + timeFmt.format(Date(inv.completedAt ?: inv.createdAt)),
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(Money.format(inv.total), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        val shortcuts = listOf(
            R.string.dash_cashier to actions.onCashier,
            R.string.dash_add_product to actions.onAddProduct,
            R.string.dash_stock_count to actions.onStockCount,
            R.string.dash_expenses_short to actions.onExpenses,
            R.string.dash_reports to actions.onReports,
        ).mapNotNull { (label, action) -> action?.let { label to it } }
        if (shortcuts.isNotEmpty()) {
            item { Text(stringResource(R.string.dash_shortcuts), style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shortcuts) { (label, action) ->
                        OutlinedButton(onClick = action) { Text(stringResource(label)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
    }
}

/** Net sales (tax excluded) of each of the last days. A day with net returns above sales is drawn as an empty bar. */
@Composable
private fun WeekChart(points: List<DayPoint>) {
    val fmt = remember { SimpleDateFormat("dd/MM", Locale.US) }
    val max = (points.maxOfOrNull { it.netSales } ?: 0L).coerceAtLeast(1L)
    val maxHeight: Dp = 100.dp
    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        points.forEach { p ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.fillMaxWidth()
                        .height((maxHeight.value * (p.netSales.coerceAtLeast(0L).toFloat() / max)).dp.coerceAtLeast(2.dp))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                )
                Text(fmt.format(Date(p.dayStart)), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
