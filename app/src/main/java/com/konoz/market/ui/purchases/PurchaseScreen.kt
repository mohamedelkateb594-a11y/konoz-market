package com.konoz.market.ui.purchases

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import com.konoz.market.core.ExpiryDates
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Product
import com.konoz.market.ui.scanner.CameraBarcodeScanner

@Composable
fun PurchaseScreen(vm: PurchaseViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    var showScan by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val entry = state.entry
    val totals = entry.totals()
    val isReturn = vm.isReturn

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Column {
                Text(stringResource(if (isReturn) R.string.purchase_return_title else R.string.purchase_title), style = MaterialTheme.typography.titleLarge)
                Text(state.supplierName, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.purchase_search_hint)) },
                singleLine = true,
            )
            IconButton(onClick = { showScan = true }) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
            }
        }

        state.error?.let { e ->
            Text(
                errorText(e, state.errorArg),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (query.isNotBlank()) {
                ResultsList(results, onPick = { vm.addProduct(it.id) })
            } else if (entry.lines.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.purchase_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(entry.lines, key = { _, l -> l.productId }) { index, line ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(line.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { vm.removeLine(index) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField(stringResource(R.string.line_qty), line.qty, Modifier.weight(1f)) { v -> vm.editLine(index) { it.copy(qty = v) } }
                                    NumberField(stringResource(R.string.line_cost), line.cost, Modifier.weight(1f)) { v -> vm.editLine(index) { it.copy(cost = v) } }
                                    NumberField(stringResource(R.string.line_discount), line.discount, Modifier.weight(1f)) { v -> vm.editLine(index) { it.copy(discount = v) } }
                                }
                                if (!isReturn) {
                                    ExpiryButton(line.expiryUtcMillis) { m -> vm.editLine(index) { it.copy(expiryUtcMillis = m) } }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stringResource(R.string.invoice_discount), entry.invoiceDiscount, Modifier.weight(1f)) { vm.setInvoiceDiscount(it) }
                    NumberField(stringResource(if (isReturn) R.string.refund_label else R.string.paid_label_purchase), entry.paid, Modifier.weight(1f)) { vm.setPaid(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.total_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(totals?.total ?: 0L), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (totals != null) {
                    OutlinedButton(onClick = { vm.setPaid(Money.fromMinor(totals.total).toPlainString()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pay_full))
                    }
                }
                Button(
                    onClick = { showConfirm = true },
                    enabled = totals != null && !state.saving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.approve), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }

    if (showScan) {
        AlertDialog(
            onDismissRequest = { showScan = false },
            title = { Text(stringResource(R.string.scan_barcode_title)) },
            text = {
                CameraBarcodeScanner(
                    enabled = true,
                    onBarcode = { code -> vm.scan(code); showScan = false },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
            },
            confirmButton = { TextButton(onClick = { showScan = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    state.unknownBarcode?.let {
        AlertDialog(
            onDismissRequest = { vm.clearUnknownBarcode() },
            text = { Text(stringResource(R.string.purchase_unknown_barcode)) },
            confirmButton = { TextButton(onClick = { vm.clearUnknownBarcode() }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (showConfirm && totals != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.approve_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.total_label) + ": " + Money.format(totals.total), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (isReturn) R.string.approve_return_body else R.string.approve_confirm_body))
                }
            },
            confirmButton = { Button(onClick = { showConfirm = false; vm.approve() }) { Text(stringResource(R.string.approve)) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    state.done?.let { r ->
        AlertDialog(
            onDismissRequest = onClose,
            text = { Text(stringResource(R.string.purchase_saved_fmt, r.number.toString())) },
            confirmButton = { Button(onClick = onClose) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun ResultsList(results: List<Product>, onPick: (Product) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(results, key = { _, p -> p.id }) { _, p ->
            Card(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(p.costPrice), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryButton(millis: Long?, onChange: (Long?) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { show = true }) {
            Text(if (millis == null) stringResource(R.string.field_expiry) else ExpiryDates.format(millis))
        }
        if (millis != null) TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.clear_date)) }
    }
    if (show) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { onChange(picker.selectedDateMillis); show = false }) { Text(stringResource(R.string.ok)) } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun errorText(e: PurchaseError, arg: String?): String = when (e) {
    PurchaseError.EMPTY -> stringResource(R.string.perr_empty)
    PurchaseError.INVALID -> stringResource(R.string.perr_invalid)
    PurchaseError.PAID_INVALID -> stringResource(R.string.perr_paid)
    PurchaseError.STOCK -> stringResource(R.string.perr_stock, arg.orEmpty())
    PurchaseError.SUPPLIER -> stringResource(R.string.perr_supplier)
    PurchaseError.UNKNOWN -> stringResource(R.string.perr_unknown)
}
