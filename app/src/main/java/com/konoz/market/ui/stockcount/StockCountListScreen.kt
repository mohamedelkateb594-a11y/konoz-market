package com.konoz.market.ui.stockcount

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.data.entity.CountScope
import com.konoz.market.data.entity.CountStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StockCountListScreen(
    vm: StockCountListViewModel,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts by vm.counts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var showStart by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.count_title), style = MaterialTheme.typography.titleLarge)
            }
            if (counts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.count_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(counts, key = { it.id }) { c ->
                        Card(onClick = { onOpen(c.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.count_label_fmt, c.id.toString()), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(if (c.status == CountStatus.DRAFT) R.string.count_status_draft else R.string.count_status_approved) +
                                        "  \u2022  " +
                                        stringResource(if (c.scope == CountScope.ALL) R.string.count_scope_all else R.string.count_scope_category),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (c.status == CountStatus.DRAFT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(fmt.format(Date(c.approvedAt ?: c.startedAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showStart = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.count_new))
        }
    }

    if (showStart) {
        var scope by remember { mutableStateOf(CountScope.ALL) }
        var categoryId by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { showStart = false },
            title = { Text(stringResource(R.string.count_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = scope == CountScope.ALL, onClick = { scope = CountScope.ALL }, label = { Text(stringResource(R.string.count_scope_all)) })
                        FilterChip(selected = scope == CountScope.CATEGORY, onClick = { scope = CountScope.CATEGORY }, label = { Text(stringResource(R.string.count_scope_category)) })
                    }
                    if (scope == CountScope.CATEGORY) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories, key = { it.id }) { c ->
                                FilterChip(selected = categoryId == c.id, onClick = { categoryId = c.id }, label = { Text(c.name) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.start(scope, categoryId) { id -> showStart = false; onOpen(id) } },
                    enabled = scope == CountScope.ALL || categoryId != null,
                ) { Text(stringResource(R.string.count_start)) }
            },
            dismissButton = { TextButton(onClick = { showStart = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    error?.let { e ->
        AlertDialog(
            onDismissRequest = { vm.consumeError() },
            text = {
                Text(
                    stringResource(
                        when (e) {
                            CountListError.NO_PRODUCTS -> R.string.cerr_no_products
                            CountListError.CATEGORY -> R.string.cerr_category
                            CountListError.UNKNOWN -> R.string.cerr_unknown
                        }
                    )
                )
            },
            confirmButton = { TextButton(onClick = { vm.consumeError() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
