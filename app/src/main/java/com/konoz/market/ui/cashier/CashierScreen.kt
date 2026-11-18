package com.konoz.market.ui.cashier

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.domain.CartItem
import com.konoz.market.domain.CashierMessage
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes
import com.konoz.market.ui.scanner.CameraBarcodeScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layout, top to bottom: the camera (exactly the top quarter of the screen, clipped) with the product-search lens on it,
 * the invoice (a fixed header row, then the items as small pill rows), then the bottom panel (total, pay).
 * The search lens expands into a field with a drop-down list of products. The receipt after payment is shown by [CashierRoute].
 */
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
    var searchOpen by remember { mutableStateOf(false) }

    // Every scan that puts a product on the invoice: short beep, vibration and a banner with the product and its new quantity.
    var scanAlert by remember { mutableStateOf<ScanAlert?>(null) }
    val haptic = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    LaunchedEffect(vm) {
        vm.scanAlerts.collectLatest { alert ->
            scanAlert = alert
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
            delay(1_800)
            scanAlert = null
        }
    }

    val totals = cart.totals()
    val anyDialog = message != null || showPay || showHeld || showInvoiceDiscount || editId != null || lastSale != null || searchOpen

    fun closeSearch() {
        vm.setQuery("")
        searchOpen = false
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- the camera: the top quarter of the screen; only the search lens and the close button are on it ----
            if (scanning) {
                val cameraHeight = (LocalConfiguration.current.screenHeightDp / 4).dp
                Box(Modifier.fillMaxWidth().height(cameraHeight).clipToBounds()) {
                    CameraBarcodeScanner(enabled = !anyDialog, onBarcode = { vm.onScan(it) }, modifier = Modifier.fillMaxSize())
                    SearchLens(onClick = { searchOpen = true }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                    IconButton(onClick = { vm.setScanning(false) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_camera))
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchLens(onClick = { searchOpen = true })
                    OutlinedButton(onClick = { vm.setScanning(true) }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.open_camera))
                    }
                }
            }

            // ---- the invoice, directly under the camera (clipped so it never draws over it) ----
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                if (cart.isEmpty) {
                    Column(Modifier.fillMaxSize()) {
                        if (favorites.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(favorites, key = { it.id }) { p ->
                                    AssistChip(onClick = { vm.addProduct(p.id) }, label = { Text(p.name) })
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.cart_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        // The header row stays in place; only the items under it scroll.
                        CartHeader(Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(cart.items, key = { it.productId }) { item ->
                                CartRow(
                                    item = item,
                                    onClick = { editId = item.productId },
                                    onPlus = { vm.changeQty(item.productId, 1) },
                                    onMinus = { vm.changeQty(item.productId, -1) },
                                    onRemove = { vm.remove(item.productId) },
                                )
                            }
                        }
                    }
                }
                scanAlert?.let { alert ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 6.dp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.scan_added_fmt, alert.name, formatQty(alert.qtyMilli)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            // ---- bottom panel: total, pay ----
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (totals != null) {
                        val discounts = totals.itemDiscountTotal + totals.invoiceDiscount
                        if (discounts > 0L) {
                            Text(stringResource(R.string.discounts_fmt, Money.format(discounts)), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (totals.taxAmount > 0L) {
                            Text(stringResource(R.string.tax_fmt, Money.format(totals.taxAmount)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.total_label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                Money.format(totals?.total ?: 0L),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Button(
                            onClick = { showPay = true },
                            enabled = !cart.isEmpty,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(stringResource(R.string.pay), style = MaterialTheme.typography.titleMedium) }
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
                }
            }
        }

        // ---- the expanded product search: a field and a drop-down list of products over the camera and the invoice ----
        if (searchOpen) {
            ProductSearchPanel(
                query = query,
                onQuery = vm::setQuery,
                products = if (query.isBlank()) favorites else results,
                onPick = { p ->
                    vm.addProduct(p.id)
                    closeSearch()
                },
                onClose = ::closeSearch,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 8.dp),
            )
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

    message?.let { m ->
        MessageDialog(
            message = m,
            onRegister = { code -> vm.consumeMessage(); onRegisterBarcode(code) },
            onConfirmExpired = { vm.confirmExpiredSale() },
            onDismiss = { vm.consumeMessage() },
        )
    }
}

/** The collapsed search: a magnifier with its label under it. Tapping it expands [ProductSearchPanel]. */
@Composable
private fun SearchLens(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(
                stringResource(R.string.cs_search_chip),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** The expanded search: a field, and under it the matching products (the favorites while the field is empty). Tap one to add it. */
@Composable
private fun ProductSearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    products: List<Product>,
    onPick: (Product) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, shadowElevation = 10.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.cashier_search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cs_search_close))
                }
            }
            LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(products, key = { it.id }) { p ->
                    Surface(
                        onClick = { onPick(p) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(Money.format(p.salePrice), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

// Column weights shared by the header row and the item rows so the columns line up: quantity, price, product, total, delete.
private const val QTY_W = 1.5f
private const val PRICE_W = 1.1f
private const val NAME_W = 2.2f
private const val TOTAL_W = 1.2f

/** The fixed header of the invoice: quantity, price, product, line total. */
@Composable
private fun CartHeader(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(stringResource(R.string.cart_col_qty), QTY_W)
            HeaderCell(stringResource(R.string.cart_col_price), PRICE_W)
            HeaderCell(stringResource(R.string.cart_col_name), NAME_W)
            HeaderCell(stringResource(R.string.cart_col_total), TOTAL_W)
            Spacer(Modifier.width(28.dp))
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** One invoice line as a small pill: minus / quantity / plus, unit price, product (with its unit and discount), line total, delete. */
@Composable
private fun CartRow(
    item: CartItem,
    onClick: () -> Unit,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(QTY_W), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = onMinus, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    formatQty(item.qtyMilli),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPlus, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                Money.format(item.unitPrice),
                modifier = Modifier.weight(PRICE_W),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(Modifier.weight(NAME_W).padding(horizontal = 4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val unit = stringResource(unitLabelRes(item.unit))
                Text(
                    if (item.discount > 0L) unit + " - " + stringResource(R.string.line_discount_label) + " " + Money.format(item.discount) else unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.discount > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                Money.format(item.net),
                modifier = Modifier.weight(TOTAL_W),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cashier_remove_item),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
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
    is CashierMessage.NoOpenShift -> stringResource(R.string.msg_no_open_shift)
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
