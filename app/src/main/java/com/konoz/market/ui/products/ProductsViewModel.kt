package com.konoz.market.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Percent
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.Product
import com.konoz.market.data.repo.ImportResult
import com.konoz.market.data.repo.ProductCsvService
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProductsNotice {
    data class BulkDone(val count: Int) : ProductsNotice
    data class ImportDone(val result: ImportResult) : ProductsNotice
    data object ExportDone : ProductsNotice
    data object Failed : ProductsNotice
}

class ProductsViewModel(
    private val repo: ProductRepository,
    private val csv: ProductCsvService,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow<Long?>(null)
    val categoryFilter: StateFlow<Long?> = _filter

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<Product>> = combine(_query.debounce(150), _filter) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            repo.observeProducts(q).map { list -> if (f == null) list else list.filter { it.categoryId == f } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<ProductsNotice?>(null)
    val notice: StateFlow<ProductsNotice?> = _notice.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setCategoryFilter(id: Long?) {
        _filter.value = id
    }

    fun setActive(p: Product, active: Boolean) {
        viewModelScope.launch { repo.setActive(p.id, active, userIdProvider()) }
    }

    fun toggleFavorite(p: Product) {
        viewModelScope.launch { repo.toggleFavorite(p.id) }
    }

    /** percentText like "10" or "-5"; categoryId null = all active products. */
    fun bulkAdjust(categoryId: Long?, percentText: String) {
        val bp = Percent.parseBasisPoints(percentText)
        if (bp == null) {
            notify(ProductsNotice.Failed)
            return
        }
        viewModelScope.launch {
            try {
                notify(ProductsNotice.BulkDone(repo.bulkAdjustSalePrice(categoryId, bp, userIdProvider())))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(ProductsNotice.Failed)
            }
        }
    }

    suspend fun buildCsv(): String = csv.exportCsv()

    fun importCsv(text: String) {
        viewModelScope.launch {
            try {
                notify(ProductsNotice.ImportDone(csv.importCsv(text, userIdProvider())))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(ProductsNotice.Failed)
            }
        }
    }

    fun notify(n: ProductsNotice) {
        _notice.value = n
    }

    fun consumeNotice() {
        _notice.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ProductsViewModel(app.productRepository, app.productCsvService, app::activeUserId)
            }
        }
    }
}
