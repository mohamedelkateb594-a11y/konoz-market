package com.konoz.market.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.Product
import com.konoz.market.data.repo.CatalogRepository
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Read-only view of one product for the details screens. [product] is null while loading or when it no longer exists. */
data class ProductDetailUi(
    val loading: Boolean = true,
    val product: Product? = null,
    val barcodes: List<BarcodeRow> = emptyList(),
    val categoryName: String? = null,
    val supplierName: String? = null,
)

/**
 * Loads one product once (read only). The details screen is created again after the edit form closes (new key per open),
 * so it always shows what was just saved. No business logic lives here: it only reads through the existing repositories.
 */
class ProductDetailViewModel(
    private val repo: ProductRepository,
    private val catalog: CatalogRepository,
    private val productId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductDetailUi())
    val state: StateFlow<ProductDetailUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val d = repo.getDetails(productId)
                if (d == null) {
                    _state.value = ProductDetailUi(loading = false)
                } else {
                    val p = d.product
                    val category = p.categoryId?.let { id -> repo.observeCategories().first().firstOrNull { it.id == id }?.name }
                    val supplier = p.supplierId?.let { id -> catalog.observeSuppliers().first().firstOrNull { it.id == id }?.name }
                    _state.value = ProductDetailUi(
                        loading = false,
                        product = p,
                        barcodes = d.barcodes.map { BarcodeRow(it.code, it.unitsPerScan.toString()) },
                        categoryName = category,
                        supplierName = supplier,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ProductDetailUi(loading = false)
            }
        }
    }

    companion object {
        fun factory(productId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ProductDetailViewModel(app.productRepository, app.catalogRepository, productId)
            }
        }
    }
}
