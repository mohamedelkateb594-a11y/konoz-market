package com.konoz.market.ui.cashier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.domain.CartItem
import com.konoz.market.domain.CashierMessage
import com.konoz.market.domain.SaleResult
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes
import com.konoz.market.ui.scanner.CameraBarcodeScanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CashierScreen(
    vm: CashierViewModel,
    onRegisterBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cart by vm.cart.collectAsStateWithLifecycle()
    val held by vm.held.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val lastSale by vm.lastSale.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val canOverridePrice by vm.canOverridePrice.collectAsStateWithLifecycle()

    var showPay by remember { mutableStateOf(false) }
    var showHeld by remember { mutableStateOf(false) }
    var showInvoiceDiscount by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }

    val totals = cart.totals()
    val anyDialog = message != null || showPay || showHeld || showInvoiceDiscount || editId != null || lastSale != null

    Column(modifier.fillMaxSize()) {
        if (scanning) {
            Box(Modifier.fillMaxWidth().height(170.dp)) {
                CameraBarcodeScanner(enabled = !anyDialog, onBarcode = { vm.onScan(it) }, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { vm.setScanning(false) }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_camera))
                }
            }
        } else {
            OutlinedButton(
                onClick = { vm.setScanning(true) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_camera))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.cashier_search_hint)) },
            singleLine = true,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (query.isNotBlank()) {
                SearchResults(results, onPick = { vm.addProduct(it.id) })
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (favorites.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(favorites, key = { it.id }) { p ->
                                AssistChip(onClick = { vm.addProduct(p.id) }, label = { Text(p.name) })
                            }
                        }
                    }
                    if (cart.isEmpty) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.cart_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(cart.items, key = { it.productId }) { item ->
                                CartRow(
                                    item = item,
                                    onClick = { editId = item.productId },
                                    onPlus = { vm.changeQty(item.productId, 1) },
                                    onMinus = { vm.changeQty(item.productId, -1) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (totals != null) {
                    val discounts = totals.itemDiscountTotal + totals.invoiceDiscount
                    if (discounts > 0L) {
                        Text(stringResource(R.string.discounts_fmt, Money.format(discounts)), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (totals.taxAmount > 0L) {
                        Text(stringResource(R.string.tax_fmt, Money.format(totals.taxAmount)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.total_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        Money.format(totals?.total ?: 0L),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.hold() }, enabled = !cart.isEmpty, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.hold))
                    }
                    OutlinedButton(onClick = { showHeld = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.held_count, held.size))
                    }
                    OutlinedButton(onClick = { showInvoiceDiscount = true }, enabled = !cart.isEmpty, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.invoice_discount))
                    }
                }
                Button(
                    onClick = { showPay = true },
                    enabled = !cart.isEmpty,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.pay), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }

    // ---------- dialogs ----------
    val editing = editId?.let { id -> cart.items.firstOrNull { it.productId == id } }
    if (editing != null) {
        EditLineDialog(
            item = editing,
            canOverridePrice = canOverridePrice,
            onApply = { q, p, d -> vm.applyLineEdit(editing.productId, q, p, d); editId = null },
            onRemove = { vm.remove(editing.productId); editId = null },
            onDismiss = { editId = null },
        )
    } else if (editId != null) {
        editId = null
    }

    if (showPay && totals != null) {
        PaymentDialog(
            total = totals.total,
            onConfirm = { cash, card, wallet -> vm.pay(cash, card, wallet); showPay = false },
            onDismiss = { showPay = false },
        )
    }

    if (showInvoiceDiscount) {
        InvoiceDiscountDialog(
            current = cart.invoiceDiscount,
            onApply = { vm.setInvoiceDiscount(it); showInvoiceDiscount = false },
            onDismiss = { showInvoiceDiscount = false },
        )
    }

    if (showHeld) {
        HeldDialog(
            held = held,
            onResume = { vm.resume(it); showHeld = false },
            onDiscard = { vm.discardHeld(it) },
            onDismiss = { showHeld = false },
        )
    }

    lastSale?.let { ReceiptDialog(it, onDone = { vm.dismissSale() }) }

    message?.let { m ->
        MessageDialog(
            message = m,
            onRegister = { code -> vm.consumeMessage(); onRegisterBarcode(code) },
            onConfirmExpired = { vm.confirmExpiredSale() },
            onDismiss = { vm.consumeMessage() },
        )
    }
}

@Composable
private fun SearchResults(results: List<Product>, onPick: (Product) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.id }) { p ->
            Card(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(p.salePrice), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun CartRow(item: CartItem, onClick: () -> Unit, onPlus: () -> Unit, onMinus: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    Money.format(item.unitPrice) + " \u00D7 " + formatQty(item.qtyMilli) + " " + stringResource(unitLabelRes(item.unit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.discount > 0L) {
                    Text(
                        stringResource(R.string.line_discount_label) + ": " + Money.format(item.discount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(Money.format(item.net), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = null) }
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = null) }
        }
    }
}

@Composable
private fun EditLineDialog(
    item: CartItem,
    canOverridePrice: Boolean,
    onApply: (String, String, String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var qty by remember(item.productId) { mutableStateOf(formatQty(item.qtyMilli)) }
    var price by remember(item.productId) { mutableStateOf(Money.fromMinor(item.unitPrice).toPlainString()) }
    var discount by remember(item.productId) { mutableStateOf(Money.fromMinor(item.discount).toPlainString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(stringResource(R.string.qty_label), qty) { qty = it }
                NumberField(stringResource(R.string.price_label), price, enabled = canOverridePrice) { price = it }
                NumberField(stringResource(R.string.line_discount_label), discount) { discount = it }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(qty, price, discount) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun PaymentDialog(total: Long, onConfirm: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var cash by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    var wallet by remember { mutableStateOf("") }
    val c = Money.parseToMinor(cash.ifBlank { "0" })
    val d = Money.parseToMinor(card.ifBlank { "0" })
    val w = Money.parseToMinor(wallet.ifBlank { "0" })
    val valid = c != null && d != null && w != null
    val paid = (c ?: 0L) + (d ?: 0L) + (w ?: 0L)
    val diff = paid - total
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.total_label) + ": " + Money.format(total),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = { cash = Money.fromMinor(total).toPlainString(); card = ""; wallet = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pay_full_cash))
                }
                NumberField(stringResource(R.string.cash), cash) { cash = it }
                NumberField(stringResource(R.string.card), card) { card = it }
                NumberField(stringResource(R.string.wallet), wallet) { wallet = it }
                if (valid) {
                    if (diff >= 0L) {
                        Text(stringResource(R.string.change_fmt, Money.format(diff)), style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(
                            stringResource(R.string.remaining_fmt, Money.format(-diff)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(cash, card, wallet) }, enabled = valid && diff >= 0L) {
                Text(stringResource(R.string.confirm_payment))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun InvoiceDiscountDialog(current: Long, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(Money.fromMinor(current).toPlainString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invoice_discount)) },
        text = { NumberField(stringResource(R.string.invoice_discount), value) { value = it } },
        confirmButton = { TextButton(onClick = { onApply(value) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun HeldDialog(
    held: List<SalesInvoice>,
    onResume: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("HH:mm dd/MM", Locale.US) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.held_title)) },
        text = {
            if (held.isEmpty()) {
                Text(stringResource(R.string.no_held))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(held, key = { it.id }) { inv ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Money.format(inv.total), style = MaterialTheme.typography.titleMedium)
                                Text(fmt.format(Date(inv.createdAt)), style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = { onResume(inv.id) }) { Text(stringResource(R.string.resume)) }
                            TextButton(onClick = { onDiscard(inv.id) }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun ReceiptDialog(sale: SaleResult, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.receipt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.invoice_no_fmt, sale.number.toString()), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.receipt_total_fmt, Money.format(sale.total)), style = MaterialTheme.typography.bodyLarge)
                if (sale.changeDue > 0L) {
                    Text(stringResource(R.string.receipt_change_fmt, Money.format(sale.changeDue)), style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun MessageDialog(
    message: CashierMessage,
    onRegister: (String) -> Unit,
    onConfirmExpired: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (message) {
        is CashierMessage.UnknownBarcode -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.msg_unknown_barcode_title)) },
            text = { Text(stringResource(R.string.msg_unknown_barcode_body, message.code)) },
            confirmButton = { Button(onClick = { onRegister(message.code) }) { Text(stringResource(R.string.msg_register_product)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is CashierMessage.ConfirmExpiredSale -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.msg_confirm_expired_title)) },
            text = { Text(stringResource(R.string.msg_confirm_expired_body)) },
            confirmButton = { Button(onClick = { onConfirmExpired(); onDismiss() }) { Text(stringResource(R.string.msg_continue_sale)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(infoText(message)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun infoText(m: CashierMessage): String = when (m) {
    is CashierMessage.ExpiredWarning -> stringResource(R.string.msg_expired_warning, m.productName)
    is CashierMessage.ProductInactive -> stringResource(R.string.msg_inactive, m.productName)
    is CashierMessage.PaymentShort -> stringResource(R.string.msg_payment_short, Money.format(m.total), Money.format(m.paid))
    is CashierMessage.OutOfStock -> stringResource(R.string.msg_out_of_stock, m.productName)
    is CashierMessage.NotAllowed -> stringResource(R.string.msg_not_allowed)
    is CashierMessage.InvalidInput -> stringResource(R.string.msg_invalid_input)
    is CashierMessage.EmptyCart -> stringResource(R.string.msg_empty_cart)
    is CashierMessage.CardOverpay -> stringResource(R.string.msg_card_overpay)
    is CashierMessage.SaleFailed -> stringResource(R.string.msg_sale_failed)
    is CashierMessage.UnknownBarcode, is CashierMessage.ConfirmExpiredSale -> ""
}

@Composable
private fun NumberField(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
