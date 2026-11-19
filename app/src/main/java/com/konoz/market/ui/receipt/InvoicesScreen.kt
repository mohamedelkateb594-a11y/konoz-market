package com.konoz.market.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.domain.InvoiceData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InvoicesScreen(vm: InvoicesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val timeFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.inv_title), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = ui.query, onValueChange = vm::onQuery, singleLine = true,
            label = { Text(stringResource(R.string.inv_search)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (ui.shown.isEmpty()) {
            Text(
                stringResource(R.string.inv_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.shown, key = { it.id }) { inv ->
                Card(onClick = { vm.open(inv.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.inv_number_fmt, inv.number ?: 0L), style = MaterialTheme.typography.titleMedium)
                            Text(timeFmt.format(Date(inv.completedAt ?: inv.createdAt)), style = MaterialTheme.typography.bodyMedium)
                            inv.customerName?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(Money.format(inv.total), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    InvoiceShareDialog(ui, vm)
}

/**
 * The invoice picked in [ui] with the customer phone and name fields and the send/print buttons. Shows nothing when no invoice
 * is selected. [header] is extra content above the invoice (the cashier shows the payment result and change there);
 * [focusPhone] puts the cursor in the phone field with the keyboard open; the keyboard Send key (on the name field) sends the
 * PDF on WhatsApp; [closeLabelRes] labels the close button.
 */
@Composable
fun InvoiceShareDialog(
    ui: InvoicesUi,
    vm: InvoicesViewModel,
    header: (@Composable () -> Unit)? = null,
    focusPhone: Boolean = false,
    closeLabelRes: Int = R.string.ok,
) {
    val data = ui.selected ?: return
    val phoneFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = vm::close,
        title = { Text(stringResource(R.string.inv_number_fmt, data.number)) },
        text = {
            if (focusPhone) {
                LaunchedEffect(data.number) { runCatching { phoneFocus.requestFocus() } }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                header?.invoke()
                InvoiceSummary(data)
                OutlinedTextField(
                    value = ui.phone, onValueChange = vm::onPhone, singleLine = true,
                    label = { Text(stringResource(R.string.inv_phone)) },
                    placeholder = { Text(stringResource(R.string.inv_phone_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(phoneFocus),
                )
                OutlinedTextField(
                    value = ui.name, onValueChange = vm::onName, singleLine = true,
                    label = { Text(stringResource(R.string.inv_customer_name)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.sendPdf() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                ui.message?.let {
                    Text(
                        stringResource(
                            when (it) {
                                ShareMessage.INVALID_PHONE -> R.string.inv_err_phone
                                ShareMessage.NAME_REQUIRED -> R.string.inv_err_name
                                ShareMessage.NO_WHATSAPP -> R.string.inv_err_no_whatsapp
                                ShareMessage.FAILED -> R.string.inv_err_failed
                                ShareMessage.NO_PRINTER -> R.string.inv_err_no_printer
                                ShareMessage.PRINT_FAILED -> R.string.inv_err_print
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = vm::sendPdf, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inv_send_pdf_wa))
                }
                Button(onClick = vm::sendImage, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inv_send_image_wa))
                }
                OutlinedButton(onClick = vm::sendText, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inv_send_text_wa))
                }
                OutlinedButton(onClick = vm::print, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inv_print))
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::close) { Text(stringResource(closeLabelRes)) } },
    )
}

/** The items and totals, so the cashier can check which invoice this is before sending it. */
@Composable
private fun InvoiceSummary(d: InvoiceData) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        d.lines.forEach { l ->
            Row(Modifier.fillMaxWidth()) {
                Text(l.name + "  x" + Quantities.format(l.qtyMilli), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(Money.format(l.total), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.inv_total), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(Money.format(d.total), style = MaterialTheme.typography.titleSmall)
        }
    }
}
