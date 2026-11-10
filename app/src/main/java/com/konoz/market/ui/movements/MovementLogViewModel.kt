package com.konoz.market.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.dao.StockDao
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.Product
import com.konoz.market.data.repo.ProductRepository
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

class MovementLogViewModel(
    private val repo: ProductRepository,
    private val stock: StockDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selected = MutableStateFlow<Product?>(null)
    val selected: StateFlow<Product?> = _selected.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Product>> = _query
        .debounce(150)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.observeProducts(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val movements: StateFlow<List<InventoryMovement>> = _selected
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else stock.observeMovements(p.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun select(p: Product?) {
        _query.value = ""
        _selected.value = p
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                MovementLogViewModel(app.productRepository, app.database.stock())
            }
        }
    }
}
