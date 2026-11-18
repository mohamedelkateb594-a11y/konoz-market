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
 * Navigation inside the Products tab: list -> details -> property screen, and details -> edit form.
 *  - editId: null = no form, 0 = new product, >0 = edit. The form has priority over everything else.
 *  - detailId: the product whose details are open (null = list).
 *  - section: the property screen open over the details (null = the details themselves).
 * Tapping a product card opens its details (read only); editing is the pencil on the details screen.
 * [newProductBarcode] (from the cashier's "unknown barcode" dialog) opens a new-product form with that barcode filled in.
 */
@Composable
fun ProductsRoute(
    modifier: Modifier = Modifier,
    newProductBarcode: String? = null,
    onBarcodeConsumed: () -> Unit = {},
) {
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var session by rememberSaveable { mutableIntStateOf(0) } // new key per open, so a form or details never show stale data
    var prefill by remember { mutableStateOf<String?>(null) }
    val listVm: ProductsViewModel = viewModel(factory = ProductsViewModel.Factory)

    LaunchedEffect(newProductBarcode) {
        if (newProductBarcode != null) {
            session++
            detailId = null
            sectionName = null
            editId = 0L
            prefill = newProductBarcode
            onBarcodeConsumed()
        }
    }

    val formId = editId
    val detail = detailId
    when {
        formId != null -> {
            // Closing the form returns to the details it was opened from (or the list), and reloads them.
            val closeForm = { editId = null; session++ }
            BackHandler { closeForm() }
            val editVm: ProductEditViewModel = viewModel(key = "edit_${formId}_$session", factory = ProductEditViewModel.factory(formId))
            LaunchedEffect(formId, session) {
                prefill?.let { code ->
                    editVm.update { f -> f.copy(barcodes = listOf(BarcodeRow(code = code))) }
                    prefill = null
                }
            }
            ProductEditScreen(vm = editVm, onClose = closeForm, modifier = modifier)
        }
        detail != null -> {
            val detailVm: ProductDetailViewModel = viewModel(key = "detail_${detail}_$session", factory = ProductDetailViewModel.factory(detail))
            val section = sectionName?.let { name -> ProductDetailSection.entries.firstOrNull { it.name == name } }
            if (section == null) {
                BackHandler { detailId = null }
                ProductDetailScreen(
                    vm = detailVm,
                    onBack = { detailId = null },
                    onEdit = { session++; editId = detail },
                    onOpenSection = { sectionName = it.name },
                    modifier = modifier,
                )
            } else {
                BackHandler { sectionName = null }
                ProductSectionScreen(vm = detailVm, section = section, onBack = { sectionName = null }, modifier = modifier)
            }
        }
        else -> ProductsScreen(
            vm = listVm,
            onAdd = { session++; editId = 0L },
            onOpen = { session++; sectionName = null; detailId = it },
            modifier = modifier,
        )
    }
}
