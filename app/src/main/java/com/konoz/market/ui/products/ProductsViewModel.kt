package com.konoz.market.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.Product
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductsViewModel(
    private val repo: ProductRepository,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<Product>> = _query
        .debounce(150)
        .flatMapLatest { repo.observeProducts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setActive(p: Product, active: Boolean) {
        viewModelScope.launch { repo.setActive(p.id, active, userIdProvider()) }
    }

    fun toggleFavorite(p: Product) {
        viewModelScope.launch { repo.toggleFavorite(p.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ProductsViewModel(app.productRepository, app::activeUserId)
            }
        }
    }
}
