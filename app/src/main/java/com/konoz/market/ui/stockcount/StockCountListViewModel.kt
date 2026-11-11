package com.konoz.market.ui.stockcount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.dao.StockCountDao
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.CountScope
import com.konoz.market.data.entity.StockCount
import com.konoz.market.data.repo.ProductRepository
import com.konoz.market.domain.CountException
import com.konoz.market.domain.StockCountService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CountListError { NO_PRODUCTS, CATEGORY, UNKNOWN }

class StockCountListViewModel(
    dao: StockCountDao,
    private val service: StockCountService,
    repo: ProductRepository,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    /** Newest first: drafts and the approved history in one list. */
    val counts: StateFlow<List<StockCount>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<CountListError?>(null)
    val error: StateFlow<CountListError?> = _error.asStateFlow()

    fun start(scope: CountScope, categoryId: Long?, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onStarted(service.start(scope, categoryId, userIdProvider()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: CountException) {
                _error.value = when (e) {
                    is CountException.NoProducts -> CountListError.NO_PRODUCTS
                    is CountException.CategoryRequired -> CountListError.CATEGORY
                    else -> CountListError.UNKNOWN
                }
            } catch (e: Exception) {
                _error.value = CountListError.UNKNOWN
            }
        }
    }

    fun consumeError() {
        _error.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                StockCountListViewModel(app.database.stockCounts(), app.stockCountService, app.productRepository, app::activeUserId)
            }
        }
    }
}
