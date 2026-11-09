package com.konoz.market.ui.products

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.ExpiryDates
import com.konoz.market.core.Money
import com.konoz.market.data.entity.UnitType
import com.konoz.market.ui.common.ProductImage
import com.konoz.market.ui.common.ProductImages
import com.konoz.market.ui.common.unitLabelRes
import com.konoz.market.ui.scanner.CameraBarcodeScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductEditScreen(
    vm: ProductEditViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    val form = state.form

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanIndex by remember { mutableStateOf<Int?>(null) }
    var showNewCategory by remember { mutableStateOf(false) }
    var showNewSupplier by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { ProductImages.save(context, uri) }
            if (path != null) vm.update { it.copy(imagePath = path) }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                stringResource(if (form.isEdit) R.string.edit_product else R.string.new_product),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = vm::save, enabled = !state.saving && !state.loading) { Text(stringResource(R.string.save)) }
        }

        state.error?.let { e ->
            Text(
                errorText(e, state.errorArg),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImage(form.imagePath, 88.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.product_image), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(stringResource(R.string.choose_image))
                        }
                        if (form.imagePath != null) {
                            TextButton(onClick = { vm.update { it.copy(imagePath = null) } }) { Text(stringResource(R.string.remove_image)) }
                        }
                    }
                }
            }

            TextField(stringResource(R.string.field_name), form.name) { v -> vm.update { it.copy(name = v) } }
            TextField(stringResource(R.string.field_internal_code), form.internalCode) { v -> vm.update { it.copy(internalCode = v) } }

            Text(stringResource(R.string.field_category), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = form.categoryId == null,
                        onClick = { vm.update { it.copy(categoryId = null) } },
                        label = { Text(stringResource(R.string.no_category)) },
                    )
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = form.categoryId == c.id,
                        onClick = { vm.update { it.copy(categoryId = c.id) } },
                        label = { Text(c.name) },
                    )
                }
                item { AssistChip(onClick = { showNewCategory = true }, label = { Text(stringResource(R.string.new_category)) }) }
            }

            Text(stringResource(R.string.field_supplier), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = form.supplierId == null,
                        onClick = { vm.update { it.copy(supplierId = null) } },
                        label = { Text(stringResource(R.string.no_supplier)) },
                    )
                }
                items(suppliers, key = { it.id }) { s ->
                    FilterChip(
                        selected = form.supplierId == s.id,
                        onClick = { vm.update { it.copy(supplierId = s.id) } },
                        label = { Text(s.name) },
                    )
                }
                item { AssistChip(onClick = { showNewSupplier = true }, label = { Text(stringResource(R.string.new_supplier)) }) }
            }

            Text(stringResource(R.string.field_unit), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitType.values().forEach { u ->
                    FilterChip(
                        selected = form.unit == u,
                        onClick = { vm.update { it.copy(unit = u) } },
                        label = { Text(stringResource(unitLabelRes(u))) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(stringResource(R.string.field_cost), form.cost, Modifier.weight(1f), KeyboardType.Decimal) { v -> vm.update { it.copy(cost = v) } }
                TextField(stringResource(R.string.field_sale_price), form.price, Modifier.weight(1f), KeyboardType.Decimal) { v -> vm.update { it.copy(price = v) } }
            }
            MarginText(form.cost, form.price)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(stringResource(R.string.field_low_stock), form.lowStock, Modifier.weight(1f), KeyboardType.Decimal) { v -> vm.update { it.copy(lowStock = v) } }
                TextField(stringResource(R.string.field_pieces_per_carton), form.piecesPerCarton, Modifier.weight(1f), KeyboardType.Number) { v -> vm.update { it.copy(piecesPerCarton = v) } }
            }

            if (!form.isEdit) {
                TextField(stringResource(R.string.field_opening_qty), form.openingQty, Modifier, KeyboardType.Decimal) { v -> vm.update { it.copy(openingQty = v) } }
                SwitchRow(stringResource(R.string.opening_in_cartons), form.openingInCartons) { v -> vm.update { it.copy(openingInCartons = v) } }
            }

            ExpiryField(form.expiryUtcMillis) { m -> vm.update { it.copy(expiryUtcMillis = m) } }

            Text(stringResource(R.string.field_barcodes), style = MaterialTheme.typography.titleMedium)
            form.barcodes.forEachIndexed { i, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(stringResource(R.string.barcode_code), row.code, Modifier.weight(1f)) { v ->
                        vm.update { f -> f.copy(barcodes = f.barcodes.toMutableList().also { l -> l[i] = row.copy(code = v) }) }
                    }
                    IconButton(onClick = { scanIndex = i }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
                    }
                    TextField(stringResource(R.string.barcode_units), row.unitsPerScan, Modifier.width(88.dp), KeyboardType.Number) { v ->
                        vm.update { f -> f.copy(barcodes = f.barcodes.toMutableList().also { l -> l[i] = row.copy(unitsPerScan = v) }) }
                    }
                    if (form.barcodes.size > 1) {
                        IconButton(onClick = { vm.update { f -> f.copy(barcodes = f.barcodes.toMutableList().also { l -> l.removeAt(i) }) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { vm.update { f -> f.copy(barcodes = f.barcodes + BarcodeRow()) } }) {
                Text(stringResource(R.string.add_barcode))
            }

            SwitchRow(stringResource(R.string.favorite_label), form.favorite) { v -> vm.update { it.copy(favorite = v) } }
            SwitchRow(stringResource(R.string.active_label), form.active) { v -> vm.update { it.copy(active = v) } }
        }
    }

    scanIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { scanIndex = null },
            title = { Text(stringResource(R.string.scan_barcode_title)) },
            text = {
                CameraBarcodeScanner(
                    enabled = true,
                    onBarcode = { code ->
                        vm.update { f ->
                            if (index in f.barcodes.indices) {
                                f.copy(barcodes = f.barcodes.toMutableList().also { l -> l[index] = l[index].copy(code = code) })
                            } else f
                        }
                        scanIndex = null
                    },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
            },
            confirmButton = { TextButton(onClick = { scanIndex = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showNewCategory) {
        NewItemDialog(
            title = stringResource(R.string.new_category),
            nameLabel = stringResource(R.string.new_category_name),
            extraLabel = null,
            onConfirm = { name, _ -> vm.addCategory(name); showNewCategory = false },
            onDismiss = { showNewCategory = false },
        )
    }
    if (showNewSupplier) {
        NewItemDialog(
            title = stringResource(R.string.new_supplier),
            nameLabel = stringResource(R.string.supplier_name),
            extraLabel = stringResource(R.string.supplier_phone),
            onConfirm = { name, phone -> vm.addSupplier(name, phone); showNewSupplier = false },
            onDismiss = { showNewSupplier = false },
        )
    }
}

@Composable
private fun NewItemDialog(
    title: String,
    nameLabel: String,
    extraLabel: String?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (extraLabel != null) {
                    OutlinedTextField(
                        value = extra, onValueChange = { extra = it }, label = { Text(extraLabel) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, extra) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.then(if (modifier == Modifier) Modifier.fillMaxWidth() else Modifier),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MarginText(cost: String, price: String) {
    val c = Money.parseToMinor(cost.ifBlank { "0" })
    val p = Money.parseToMinor(price.ifBlank { "0" })
    if (c == null || p == null || p <= 0L) return
    val bp = Money.marginBasisPoints(c, p)
    val text = stringResource(R.string.field_margin) + ": " + java.math.BigDecimal.valueOf(bp.toLong(), 2).toPlainString() + "%"
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (bp < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryField(millis: Long?, onChange: (Long?) -> Unit) {
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
            confirmButton = {
                TextButton(onClick = { onChange(picker.selectedDateMillis); show = false }) { Text(stringResource(R.string.ok)) }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun errorText(e: FormError, arg: String?): String = when (e) {
    FormError.NAME_REQUIRED -> stringResource(R.string.err_name_required)
    FormError.PRICE_INVALID -> stringResource(R.string.err_price_invalid)
    FormError.CARTON_INVALID -> stringResource(R.string.err_carton_invalid)
    FormError.BARCODE_DUPLICATE -> stringResource(R.string.err_barcode_duplicate, arg.orEmpty())
    FormError.CODE_DUPLICATE -> stringResource(R.string.err_code_duplicate, arg.orEmpty())
    FormError.QTY_INVALID -> stringResource(R.string.err_qty_invalid)
    FormError.UNKNOWN -> stringResource(R.string.err_unknown)
}
