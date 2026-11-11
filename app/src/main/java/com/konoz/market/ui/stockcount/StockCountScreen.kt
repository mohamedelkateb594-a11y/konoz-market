package com.konoz.market.ui.stockcount

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.CountStatus
import com.konoz.market.domain.CountLine
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes
import com.konoz.market.ui.scanner.CameraBarcodeScanner

@Composable
fun StockCountScreen(vm: StockCountViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val header by vm.header.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val lines by vm.visibleLines.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val approval by vm.approval.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    var showScan by remember { mutableStateOf(false) }
    var showApprove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val editable = header?.status == CountStatus.DRAFT
    LaunchedEffect(deleted) { if (deleted) onBack() }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(
                stringResource(R.string.count_label_fmt, (header?.id ?: 0L).toString()),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            if (editable) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.count_delete_draft), tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.count_progress_fmt, summary.countedItems, summary.countedItems + summary.uncountedItems), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.count_shortage_fmt, Money.format(summary.shortageValue)), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.count_surplus_fmt, Money.format(summary.surplusValue)), color = MaterialTheme.colorScheme.primary)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery, modifier = Modifier.weight(1f), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.count_search_hint)) },
            )
            if (editable) {
                IconButton(onClick = { showScan = true }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode)) }
            }
        }

        error?.let {
            Text(
                stringResource(
                    when (it) {
                        CountScreenError.INVALID -> R.string.cerr_invalid
                        CountScreenError.NOT_IN_COUNT -> R.string.cerr_not_in_count
                        CountScreenError.UNKNOWN_BARCODE -> R.string.cerr_unknown_barcode
                        CountScreenError.UNKNOWN -> R.string.cerr_unknown
                    }
                ),
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines, key = { it.item.id }) { line -> CountRow(line, editable) { text -> vm.setCounted(line.item.productId, text) } }
        }

        if (editable) {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Button(onClick = { showApprove = true }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)) {
                    Text(stringResource(R.string.count_approve), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showScan) {
        AlertDialog(
            onDismissRequest = { showScan = false },
            title = { Text(stringResource(R.string.count_scan_hint)) },
            text = { CameraBarcodeScanner(enabled = true, onBarcode = { code -> vm.scan(code) }, modifier = Modifier.fillMaxWidth().height(260.dp)) },
            confirmButton = { TextButton(onClick = { showScan = false }) { Text(stringResource(R.string.done)) } },
        )
    }

    if (showApprove) {
        AlertDialog(
            onDismissRequest = { showApprove = false },
            title = { Text(stringResource(R.string.count_approve_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.count_shortage_fmt, Money.format(summary.shortageValue)), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.count_surplus_fmt, Money.format(summary.surplusValue)), color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.count_approve_body))
                }
            },
            confirmButton = { Button(onClick = { showApprove = false; vm.approve() }) { Text(stringResource(R.string.count_approve)) } },
            dismissButton = { TextButton(onClick = { showApprove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.count_delete_draft)) },
            text = { Text(stringResource(R.string.count_delete_body)) },
            confirmButton = { Button(onClick = { showDelete = false; vm.deleteDraft() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    approval?.let { a ->
        AlertDialog(
            onDismissRequest = { vm.consumeApproval() },
            text = { Text(stringResource(R.string.count_approved_done_fmt, a.adjustedProducts)) },
            confirmButton = { TextButton(onClick = { vm.consumeApproval() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun CountRow(line: CountLine, editable: Boolean, onCounted: (String) -> Unit) {
    var text by remember(line.item.id) { mutableStateOf(line.item.countedQtyMilli?.let { formatQty(it) }.orEmpty()) }
    // an outside change (a barcode scan) replaces the text; typing never does, because then both values already match
    LaunchedEffect(line.item.countedQtyMilli) {
        val parsed = if (text.isBlank()) null else Quantities.parseMilli(text)
        if (parsed != line.item.countedQtyMilli) text = line.item.countedQtyMilli?.let { formatQty(it) }.orEmpty()
    }
    val unit = stringResource(unitLabelRes(line.unit))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(line.productName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.count_system_fmt, formatQty(line.item.systemQtyMilli) + " " + unit),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; onCounted(it) }, enabled = editable,
                    label = { Text(stringResource(R.string.count_actual_label)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                val diff = line.diffMilli
                if (diff != null && diff != 0L) {
                    Text(
                        (if (diff > 0L) "+" else "") + formatQty(diff),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (diff < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
