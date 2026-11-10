package com.konoz.market.ui.returns

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes

@Composable
fun ReturnsScreen(vm: ReturnsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val invoice = state.invoice

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.returns_title), style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.number, onValueChange = vm::setNumber, label = { Text(stringResource(R.string.invoice_number_label)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            Button(onClick = vm::find) { Text(stringResource(R.string.find_invoice)) }
        }
        if (state.notFound) {
            Text(stringResource(R.string.invoice_not_found), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        if (invoice != null) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        stringResource(R.string.return_invoice_info_fmt, (invoice.invoice.number ?: 0L).toString(), Money.format(invoice.invoice.total)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(invoice.lines, key = { it.item.id }) { line ->
                    val pick = state.picks[line.item.id]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(line.item.productName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.return_line_info_fmt, formatQty(line.item.qtyMilli), formatQty(line.returnedMilli)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (line.remainingMilli > 0L) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pick?.qty.orEmpty(), onValueChange = { vm.setQty(line.item.id, it) },
                                        label = { Text(stringResource(R.string.return_qty_label)) }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                                    )
                                    Checkbox(checked = pick?.damaged == true, onCheckedChange = { vm.setDamaged(line.item.id, it) })
                                    Text(stringResource(R.string.return_damaged), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.reason, onValueChange = vm::setReason, label = { Text(stringResource(R.string.return_reason_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.error?.let {
                        Text(
                            stringResource(
                                when (it) {
                                    ReturnsError.EMPTY -> R.string.rerr_empty
                                    ReturnsError.EXCEEDS -> R.string.rerr_exceeds
                                    ReturnsError.INVALID -> R.string.rerr_invalid
                                    ReturnsError.UNKNOWN -> R.string.rerr_unknown
                                }
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(stringResource(R.string.return_refund_fmt, Money.format(state.refundPreview())), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = vm::submit, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.return_confirm))
                    }
                }
            }
        }
    }

    state.done?.let { r ->
        AlertDialog(
            onDismissRequest = onBack,
            text = { Text(stringResource(R.string.return_done_fmt, Money.format(r.refundAmount))) },
            confirmButton = { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.done)) } },
        )
    }
}
