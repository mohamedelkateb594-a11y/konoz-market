package com.konoz.market.ui.products

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.core.Percent
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.Product
import com.konoz.market.ui.common.ProductImage
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProductsScreen(
    vm: ProductsViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val products by vm.products.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val filter by vm.categoryFilter.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showBulk by remember { mutableStateOf(false) }
    var pendingDeactivate by remember { mutableStateOf<Product?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val text = vm.buildCsv()
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri) ?: error("no output stream")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            vm.notify(if (ok) ProductsNotice.ExportDone else ProductsNotice.Failed)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }
            }.getOrNull()
            if (text == null) vm.notify(ProductsNotice.Failed) else vm.importCsv(text)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search_products)) },
                    singleLine = true,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bulk_price_title)) },
                            onClick = { menuOpen = false; showBulk = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_csv)) },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_csv)) },
                            onClick = { menuOpen = false; exportLauncher.launch("konoz-products.csv") },
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filter == null,
                        onClick = { vm.setCategoryFilter(null) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = filter == c.id,
                        onClick = { vm.setCategoryFilter(c.id) },
                        label = { Text(c.name) },
                    )
                }
            }

            if (products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_products),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(products, key = { it.id }) { p ->
                        ProductCard(
                            product = p,
                            onClick = { onEdit(p.id) },
                            onToggleFavorite = { vm.toggleFavorite(p) },
                            onToggleActive = { on -> if (on) vm.setActive(p, true) else pendingDeactivate = p },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_product)) }
    }

    pendingDeactivate?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDeactivate = null },
            title = { Text(stringResource(R.string.confirm_deactivate_title)) },
            text = { Text(stringResource(R.string.confirm_deactivate_body, p.name)) },
            confirmButton = {
                Button(onClick = { vm.setActive(p, false); pendingDeactivate = null }) { Text(stringResource(R.string.deactivate)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeactivate = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showBulk) {
        BulkPriceDialog(
            categories = categories,
            onApply = { categoryId, percent -> vm.bulkAdjust(categoryId, percent); showBulk = false },
            onDismiss = { showBulk = false },
        )
    }

    notice?.let { n -> NoticeDialog(n, onDismiss = { vm.consumeNotice() }) }
}

@Composable
private fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    val low = product.currentQtyMilli <= product.lowStockThresholdMilli
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProductImage(product.imagePath, 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(Money.format(product.salePrice), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.stock_fmt, formatQty(product.currentQtyMilli), stringResource(unitLabelRes(product.unit))) +
                        if (low) "  \u2022  " + stringResource(R.string.low_stock) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!product.isActive) {
                    Text(stringResource(R.string.inactive), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (product.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.favorite_label),
                    tint = if (product.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = product.isActive, onCheckedChange = onToggleActive)
        }
    }
}

@Composable
private fun BulkPriceDialog(categories: List<Category>, onApply: (Long?, String) -> Unit, onDismiss: () -> Unit) {
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var percent by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bulk_price_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.bulk_scope_label), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = categoryId == null,
                            onClick = { categoryId = null },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                    }
                    items(categories, key = { it.id }) { c ->
                        FilterChip(selected = categoryId == c.id, onClick = { categoryId = c.id }, label = { Text(c.name) })
                    }
                }
                OutlinedTextField(
                    value = percent,
                    onValueChange = { percent = it },
                    label = { Text(stringResource(R.string.bulk_percent_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(categoryId, percent) }, enabled = Percent.parseBasisPoints(percent) != null) {
                Text(stringResource(R.string.bulk_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NoticeDialog(notice: ProductsNotice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            when (notice) {
                is ProductsNotice.BulkDone -> Text(stringResource(R.string.bulk_done_fmt, notice.count))
                is ProductsNotice.ExportDone -> Text(stringResource(R.string.export_done))
                is ProductsNotice.Failed -> Text(stringResource(R.string.op_failed))
                is ProductsNotice.ImportDone -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_done_fmt, notice.result.created, notice.result.updated))
                    if (notice.result.errors.isNotEmpty()) {
                        Text(stringResource(R.string.import_errors_title), style = MaterialTheme.typography.titleMedium)
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(notice.result.errors.take(20)) { e ->
                                Text(
                                    stringResource(R.string.import_error_line_fmt, e.line, e.message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
