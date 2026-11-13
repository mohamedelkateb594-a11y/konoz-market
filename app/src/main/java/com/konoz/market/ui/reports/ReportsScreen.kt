package com.konoz.market.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.KonozApp
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.domain.Permission
import com.konoz.market.domain.Permissions
import com.konoz.market.domain.PeriodSummary
import com.konoz.market.domain.ProductPerformance
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.shareTextFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** [onBack] = null when the screen is shown as a tab instead of inside the More menu. */
@Composable
fun ReportsScreen(vm: ReportsViewModel, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val summary = ui.summary
    val context = LocalContext.current
    val app = context.applicationContext as KonozApp
    val user by app.session.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.export_share_title)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                }
                Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = summary != null && !exporting,
                    onClick = {
                        val s = summary ?: return@TextButton
                        exporting = true
                        exportFailed = false
                        scope.launch {
                            val canProfit = user?.let { Permissions.has(it.role, Permission.VIEW_PROFIT) } == true
                            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            val ok = try {
                                val text = vm.csvText(canProfit) { stamp.format(Date(it)) }
                                text != null && shareTextFile(
                                    context, "konoz-report-" + day.format(Date(s.range.from)) + ".csv", text, "text/csv", shareTitle,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                false
                            }
                            exportFailed = !ok
                            exporting = false
                        }
                    },
                ) { Text(stringResource(R.string.export_csv)) }
            }
        }
        if (exportFailed) {
            item { Text(stringResource(R.string.export_failed), color = MaterialTheme.colorScheme.error) }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val periods = listOf(
                    ReportPeriod.TODAY to R.string.period_day,
                    ReportPeriod.MONTH to R.string.period_month,
                    ReportPeriod.YEAR to R.string.period_year,
                    ReportPeriod.LAST_30_DAYS to R.string.rep_period_last30,
                )
                items(periods) { (p, label) ->
                    FilterChip(selected = ui.period == p, onClick = { vm.setPeriod(p) }, label = { Text(stringResource(label)) })
                }
            }
        }

        if (ui.failed) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rep_error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = vm::refresh) { Text(stringResource(R.string.rep_retry)) }
                }
            }
        } else if (summary == null) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            item { SummaryCard(summary, ui.previous) }
            item { Section(R.string.rep_top_qty) { ProductRows(ui.topQty, showProfit = false) } }
            item { Section(R.string.rep_top_profit) { ProductRows(ui.topProfit, showProfit = true) } }
            item { Section(R.string.rep_least_sold) { ProductRows(ui.leastSold, showProfit = false) } }
            item {
                Section(R.string.rep_non_moving) {
                    if (ui.nonMoving.isEmpty()) NoData() else ui.nonMoving.forEach {
                        Line(it.name, stringResource(R.string.rep_qty_fmt, formatQty(it.currentQtyMilli)))
                    }
                }
            }
            item {
                Section(R.string.rep_categories) {
                    if (ui.categories.isEmpty()) NoData() else ui.categories.forEach {
                        Line(it.categoryName ?: stringResource(R.string.rep_uncategorized), Money.format(it.revenue) + "  (" + Money.format(it.profit) + ")")
                    }
                }
            }
            item {
                Section(R.string.rep_expenses_by) {
                    if (ui.expenses.isEmpty()) NoData() else ui.expenses.forEach { Line(stringResource(categoryLabel(it.category)), Money.format(it.total)) }
                }
            }
            item {
                Section(R.string.rep_peak_hours) {
                    val busy = ui.hours.filter { it.invoiceCount > 0 }
                    if (busy.isEmpty()) NoData() else {
                        val max = busy.maxOf { it.sales }.coerceAtLeast(1L)
                        busy.forEach { h ->
                            Bar(String.format(Locale.US, "%02d:00", h.hour), stringResource(R.string.rep_invoices_fmt, h.invoiceCount), h.sales.toFloat() / max)
                        }
                    }
                }
            }
            item {
                Section(R.string.rep_weekdays) {
                    val names = DateFormatSymbols.getInstance(Locale.getDefault()).weekdays
                    val shown = listOf(7, 1, 2, 3, 4, 5, 6).mapNotNull { d -> ui.weekdays.firstOrNull { it.dayOfWeek == d } }
                    val max = shown.maxOfOrNull { it.averageSales }?.coerceAtLeast(1L) ?: 1L
                    shown.forEach { w ->
                        Bar(names[w.dayOfWeek], Money.format(w.averageSales), w.averageSales.toFloat() / max)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(s: PeriodSummary, previous: PeriodSummary?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Money.format(s.estimatedNetProfit), style = MaterialTheme.typography.headlineMedium, color = signColor(s.estimatedNetProfit))
            Text(stringResource(R.string.rep_net_profit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (previous != null) {
                Text(stringResource(R.string.rep_previous_fmt, Money.format(previous.estimatedNetProfit)), style = MaterialTheme.typography.bodyMedium)
            }
            Line(stringResource(R.string.rep_sales_gross), Money.format(s.grossSales))
            Line(stringResource(R.string.rep_tax), Money.format(s.taxCollected))
            Line(stringResource(R.string.rep_returns), Money.format(s.returnsTotal))
            Line(stringResource(R.string.rep_net_sales), Money.format(s.netSales), strong = true)
            if (previous != null) {
                Text(stringResource(R.string.rep_previous_fmt, Money.format(previous.netSales)), style = MaterialTheme.typography.bodyMedium)
            }
            Line(stringResource(R.string.rep_cogs), Money.format(s.cogs))
            Line(stringResource(R.string.rep_gross_profit), Money.format(s.grossProfit), strong = true, color = signColor(s.grossProfit))
            Line(stringResource(R.string.rep_expenses), Money.format(s.expenses))
            Line(stringResource(R.string.rep_losses), Money.format(s.inventoryLosses))
            Line(stringResource(R.string.rep_invoices), s.invoiceCount.toString())
            Line(stringResource(R.string.rep_avg_invoice), Money.format(s.averageInvoice))
            Line(stringResource(R.string.rep_discounts), Money.format(s.discounts))
        }
    }
}

@Composable
private fun Section(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ProductRows(list: List<ProductPerformance>, showProfit: Boolean) {
    if (list.isEmpty()) {
        NoData()
        return
    }
    list.forEach { p ->
        Line(
            p.name + "  -  " + stringResource(R.string.rep_qty_fmt, formatQty(p.qtyMilli)),
            Money.format(if (showProfit) p.profit else p.revenue),
        )
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun Bar(label: String, value: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Line(label, value)
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        )
    }
}

@Composable
private fun NoData() {
    Text(stringResource(R.string.rep_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun signColor(value: Long): Color = if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

private fun categoryLabel(c: ExpenseCategory): Int = when (c) {
    ExpenseCategory.ELECTRICITY -> R.string.cat_electricity
    ExpenseCategory.WATER -> R.string.cat_water
    ExpenseCategory.RENT -> R.string.cat_rent
    ExpenseCategory.WAGES -> R.string.cat_wages
    ExpenseCategory.TRANSPORT -> R.string.cat_transport
    ExpenseCategory.MAINTENANCE -> R.string.cat_maintenance
    ExpenseCategory.OPERATING_PURCHASES -> R.string.cat_operating_purchases
    ExpenseCategory.MISC -> R.string.cat_misc
}
