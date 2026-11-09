package com.konoz.market.ui.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.Product
import com.konoz.market.data.repo.CatalogRepository
import com.konoz.market.data.repo.ProductRepository
import com.konoz.market.domain.PurchaseEntry
import com.konoz.market.domain.PurchaseEntryLine
import com.konoz.market.domain.PurchaseException
import com.konoz.market.domain.PurchaseResult
import com.konoz.market.domain.PurchaseService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class PurchaseError { EMPTY, INVALID, PAID_INVALID, STOCK, SUPPLIER, UNKNOWN }

data class PurchaseUiState(
    val supplierName: String = "",
    val entry: PurchaseEntry = PurchaseEntry(),
    val saving: Boolean = false,
    val error: PurchaseError? = null,
    val errorArg: String? = null,
    val unknownBarcode: String? = null,
    val done: PurchaseResult? = null,
)

class PurchaseViewModel(
    private val repo: ProductRepository,
    private val catalog: CatalogRepository,
    private val service: PurchaseService,
    private val supplierId: Long,
    val isReturn: Boolean,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    /** One uuid per screen: approving twice (double tap, retry) can never post twice. */
    private val uuid = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(PurchaseUiState())
    val state: StateFlow<PurchaseUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Product>> = _query
        .debounce(150)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.observeProducts(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val name = catalog.getSupplier(supplierId)?.name.orEmpty()
            _state.update { it.copy(supplierName = name) }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun addProduct(productId: Long, units: Int = 1) {
        viewModelScope.launch {
            val product = repo.getDetails(productId)?.product ?: return@launch
            edit { it.addProduct(product, units) }
            _query.value = ""
        }
    }

    fun scan(code: String) {
        viewModelScope.launch {
            val found = repo.lookupBarcode(code)
            if (found == null) {
                _state.update { it.copy(unknownBarcode = code.trim()) }
            } else {
                edit { it.addProduct(found.first, found.second) }
            }
        }
    }

    fun editLine(index: Int, transform: (PurchaseEntryLine) -> PurchaseEntryLine) {
        edit { e ->
            if (index !in e.lines.indices) e else e.copy(lines = e.lines.toMutableList().also { it[index] = transform(it[index]) })
        }
    }

    fun removeLine(index: Int) {
        edit { e -> if (index !in e.lines.indices) e else e.copy(lines = e.lines.toMutableList().also { it.removeAt(index) }) }
    }

    fun setInvoiceDiscount(text: String) {
        edit { it.copy(invoiceDiscount = text) }
    }

    fun setPaid(text: String) {
        edit { it.copy(paid = text) }
    }

    fun clearUnknownBarcode() {
        _state.update { it.copy(unknownBarcode = null) }
    }

    fun approve() {
        val entry = _state.value.entry
        if (entry.lines.isEmpty()) {
            fail(PurchaseError.EMPTY)
            return
        }
        if (entry.totals() == null) {
            fail(PurchaseError.INVALID)
            return
        }
        _state.update { it.copy(saving = true, error = null, errorArg = null) }
        viewModelScope.launch {
            try {
                val draft = entry.toDraft(uuid, supplierId, userIdProvider(), isReturn)
                if (draft == null) {
                    fail(PurchaseError.INVALID)
                    return@launch
                }
                val result = service.approve(draft)
                _state.update { it.copy(saving = false, done = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PurchaseException) {
                when (e) {
                    is PurchaseException.InsufficientStockForReturn ->
                        fail(PurchaseError.STOCK, repo.getDetails(e.productId)?.product?.name.orEmpty())
                    is PurchaseException.InvalidAmount -> fail(PurchaseError.PAID_INVALID)
                    is PurchaseException.SupplierNotFound -> fail(PurchaseError.SUPPLIER)
                    is PurchaseException.EmptyLines -> fail(PurchaseError.EMPTY)
                    is PurchaseException.InvalidQuantity, is PurchaseException.ProductUnavailable -> fail(PurchaseError.INVALID)
                    else -> fail(PurchaseError.UNKNOWN)
                }
            } catch (e: Exception) {
                fail(PurchaseError.UNKNOWN)
            }
        }
    }

    private fun edit(transform: (PurchaseEntry) -> PurchaseEntry) {
        _state.update { it.copy(entry = transform(it.entry), error = null, errorArg = null) }
    }

    private fun fail(error: PurchaseError, arg: String? = null) {
        _state.update { it.copy(saving = false, error = error, errorArg = arg) }
    }

    companion object {
        fun factory(supplierId: Long, isReturn: Boolean) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                PurchaseViewModel(
                    app.productRepository, app.catalogRepository, app.purchaseService,
                    supplierId, isReturn, app::activeUserId,
                )
            }
        }
    }
}
