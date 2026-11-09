package com.konoz.market.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.ExpiryDates
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.Supplier
import com.konoz.market.data.entity.UnitType
import com.konoz.market.data.repo.BarcodeInput
import com.konoz.market.data.repo.CatalogRepository
import com.konoz.market.data.repo.ProductDetails
import com.konoz.market.data.repo.ProductException
import com.konoz.market.data.repo.ProductInput
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FormError {
    NAME_REQUIRED, PRICE_INVALID, CARTON_INVALID, BARCODE_DUPLICATE, CODE_DUPLICATE, QTY_INVALID, UNKNOWN
}

data class BarcodeRow(val code: String = "", val unitsPerScan: String = "1")

/** All numeric fields are kept as the text the user typed, and parsed only on save. */
data class ProductForm(
    val isEdit: Boolean = false,
    val name: String = "",
    val internalCode: String = "",
    val categoryId: Long? = null,
    val supplierId: Long? = null,
    val unit: UnitType = UnitType.PIECE,
    val cost: String = "",
    val price: String = "",
    val lowStock: String = "",
    val piecesPerCarton: String = "",
    val openingQty: String = "",
    val openingInCartons: Boolean = false,
    /** UTC midnight millis from the date picker. */
    val expiryUtcMillis: Long? = null,
    val barcodes: List<BarcodeRow> = listOf(BarcodeRow()),
    val favorite: Boolean = false,
    val active: Boolean = true,
    val imagePath: String? = null,
)

data class EditUiState(
    val form: ProductForm = ProductForm(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: FormError? = null,
    val errorArg: String? = null,
    val saved: Boolean = false,
)

class ProductEditViewModel(
    private val repo: ProductRepository,
    private val catalog: CatalogRepository,
    private val productId: Long,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState(loading = productId != 0L))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suppliers: StateFlow<List<Supplier>> = catalog.observeSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (productId != 0L) {
            viewModelScope.launch {
                val d = repo.getDetails(productId)
                _state.update {
                    if (d == null) it.copy(loading = false, error = FormError.UNKNOWN) else it.copy(loading = false, form = d.toForm())
                }
            }
        }
    }

    fun update(transform: (ProductForm) -> ProductForm) {
        _state.update { it.copy(form = transform(it.form), error = null, errorArg = null) }
    }

    /** Creates the category (or reuses an existing one with the same name) and selects it. */
    fun addCategory(name: String) {
        viewModelScope.launch {
            try {
                val id = repo.addCategory(name)
                update { it.copy(categoryId = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // blank name: nothing to add
            }
        }
    }

    fun addSupplier(name: String, phone: String) {
        viewModelScope.launch {
            try {
                val id = catalog.addSupplier(name, phone)
                update { it.copy(supplierId = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // blank name: nothing to add
            }
        }
    }

    fun save() {
        val f = _state.value.form
        val cost = Money.parseToMinor(f.cost.ifBlank { "0" })
        val price = Money.parseToMinor(f.price.ifBlank { "0" })
        if (cost == null || price == null) return fail(FormError.PRICE_INVALID)
        val low = Quantities.parseMilli(f.lowStock.ifBlank { "0" }) ?: return fail(FormError.QTY_INVALID)

        val perCartonText = Money.normalizeDigits(f.piecesPerCarton).trim()
        val perCarton: Int? = if (perCartonText.isEmpty()) null else (perCartonText.toIntOrNull() ?: return fail(FormError.CARTON_INVALID))

        var opening = 0L
        if (!f.isEdit) {
            val typed = Quantities.parseMilli(f.openingQty.ifBlank { "0" }) ?: return fail(FormError.QTY_INVALID)
            opening = if (f.openingInCartons) {
                if (perCarton == null || perCarton < 1) return fail(FormError.CARTON_INVALID)
                try {
                    Quantities.cartonsToPieceMilli(typed, perCarton)
                } catch (e: ArithmeticException) {
                    return fail(FormError.QTY_INVALID)
                }
            } else typed
        }

        val barcodes = ArrayList<BarcodeInput>()
        for (row in f.barcodes) {
            if (row.code.isBlank()) continue
            val units = Money.normalizeDigits(row.unitsPerScan).trim().ifEmpty { "1" }.toIntOrNull() ?: return fail(FormError.QTY_INVALID)
            barcodes += BarcodeInput(row.code.trim(), units)
        }

        val input = ProductInput(
            name = f.name, id = productId, internalCode = f.internalCode, categoryId = f.categoryId, supplierId = f.supplierId,
            unit = f.unit, costPrice = cost, salePrice = price, lowStockThresholdMilli = low, piecesPerCarton = perCarton,
            expiryDate = f.expiryUtcMillis?.let { ExpiryDates.toStored(it) }, imagePath = f.imagePath,
            isFavorite = f.favorite, isActive = f.active, barcodes = barcodes, openingQtyMilli = opening,
        )

        _state.update { it.copy(saving = true, error = null, errorArg = null) }
        viewModelScope.launch {
            try {
                repo.save(input, userIdProvider())
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProductException) {
                when (e) {
                    is ProductException.BlankName -> fail(FormError.NAME_REQUIRED)
                    is ProductException.InvalidPrice -> fail(FormError.PRICE_INVALID)
                    is ProductException.InvalidCartonSize -> fail(FormError.CARTON_INVALID)
                    is ProductException.InvalidQuantity -> fail(FormError.QTY_INVALID)
                    is ProductException.DuplicateBarcode -> fail(FormError.BARCODE_DUPLICATE, e.code)
                    is ProductException.DuplicateInternalCode -> fail(FormError.CODE_DUPLICATE, e.code)
                    is ProductException.NotFound -> fail(FormError.UNKNOWN)
                }
            } catch (e: Exception) {
                fail(FormError.UNKNOWN)
            }
        }
    }

    private fun fail(error: FormError, arg: String? = null) {
        _state.update { it.copy(saving = false, error = error, errorArg = arg) }
    }

    private fun ProductDetails.toForm(): ProductForm {
        val p = product
        return ProductForm(
            isEdit = true,
            name = p.name,
            internalCode = p.internalCode.orEmpty(),
            categoryId = p.categoryId,
            supplierId = p.supplierId,
            unit = p.unit,
            cost = Money.fromMinor(p.costPrice).toPlainString(),
            price = Money.fromMinor(p.salePrice).toPlainString(),
            lowStock = Quantities.format(p.lowStockThresholdMilli),
            piecesPerCarton = p.piecesPerCarton?.toString().orEmpty(),
            expiryUtcMillis = p.expiryDate?.let { ExpiryDates.toPickerMillis(it) },
            barcodes = barcodes.map { BarcodeRow(it.code, it.unitsPerScan.toString()) }.ifEmpty { listOf(BarcodeRow()) },
            favorite = p.isFavorite,
            active = p.isActive,
            imagePath = p.imagePath,
        )
    }

    companion object {
        fun factory(productId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ProductEditViewModel(app.productRepository, app.catalogRepository, productId, app::activeUserId)
            }
        }
    }
}
