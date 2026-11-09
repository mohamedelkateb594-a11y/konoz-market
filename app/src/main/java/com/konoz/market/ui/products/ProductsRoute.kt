package com.konoz.market.ui.products

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * List <-> edit navigation inside the Products tab. editId: null = list, 0 = new product, >0 = edit.
 * [newProductBarcode] (from the cashier's "unknown barcode" dialog) opens a new-product form with that barcode filled in.
 */
@Composable
fun ProductsRoute(
    modifier: Modifier = Modifier,
    newProductBarcode: String? = null,
    onBarcodeConsumed: () -> Unit = {},
) {
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var session by rememberSaveable { mutableIntStateOf(0) } // new key per open, so a form never shows stale input
    var prefill by remember { mutableStateOf<String?>(null) }
    val listVm: ProductsViewModel = viewModel(factory = ProductsViewModel.Factory)

    LaunchedEffect(newProductBarcode) {
        if (newProductBarcode != null) {
            session++
            editId = 0L
            prefill = newProductBarcode
            onBarcodeConsumed()
        }
    }

    val id = editId
    if (id == null) {
        ProductsScreen(
            vm = listVm,
            onAdd = { session++; editId = 0L },
            onEdit = { session++; editId = it },
            modifier = modifier,
        )
    } else {
        BackHandler { editId = null }
        val editVm: ProductEditViewModel = viewModel(key = "edit_${id}_$session", factory = ProductEditViewModel.factory(id))
        LaunchedEffect(id, session) {
            prefill?.let { code ->
                editVm.update { f -> f.copy(barcodes = listOf(BarcodeRow(code = code))) }
                prefill = null
            }
        }
        ProductEditScreen(vm = editVm, onClose = { editId = null }, modifier = modifier)
    }
}
