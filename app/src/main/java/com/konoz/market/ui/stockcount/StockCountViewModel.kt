package com.konoz.market.ui.stockcount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Quantities
import com.konoz.market.data.dao.StockCountDao
import com.konoz.market.data.entity.StockCount
import com.konoz.market.data.repo.ProductRepository
import com.konoz.market.domain.CountApproval
import com.konoz.market.domain.CountException
import com.konoz.market.domain.CountLine
import com.konoz.market.domain.CountMath
import com.konoz.market.domain.CountSummary
import com.konoz.market.domain.StockCountService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CountScreenError { INVALID, NOT_IN_COUNT, UNKNOWN_BARCODE, UNKNOWN }

class StockCountViewModel(
    private val service: StockCountService,
    private val repo: ProductRepository,
    dao: StockCountDao,
    private val countId: Long,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    val header: StateFlow<StockCount?> = dao.observeAll()
        .map { list -> list.firstOrNull { it.id == countId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val lines: StateFlow<List<CountLine>> = service.observeLines(countId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<CountSummary> = lines.map { CountMath.summary(it.map { l -> l.item }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountSummary(0, 0, 0, 0))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val visibleLines: StateFlow<List<CountLine>> = combine(lines, _query) { l, q ->
        val needle = q.trim()
        if (needle.isEmpty()) l else l.filter { it.productName.contains(needle, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<CountScreenError?>(null)
    val error: StateFlow<CountScreenError?> = _error.asStateFlow()

    private val _approval = MutableStateFlow<CountApproval?>(null)
    val approval: StateFlow<CountApproval?> = _approval.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    /** Blank text clears the counted quantity (the product is then left alone on approval). */
    fun setCounted(productId: Long, text: String) {
        val value: Long? = if (text.isBlank()) null else Quantities.parseMilli(text)
        if (value == null && text.isNotBlank()) {
            _error.value = CountScreenError.INVALID
            return
        }
        launchGuarded { service.setCounted(countId, productId, value) }
    }

    /** Scanning a barcode adds its units (a carton barcode adds N) to the counted quantity of that product. */
    fun scan(code: String) {
        viewModelScope.launch {
            val found = repo.lookupBarcode(code)
            if (found == null) {
                _error.value = CountScreenError.UNKNOWN_BARCODE
                return@launch
            }
            val (product, units) = found
            try {
                service.incrementCounted(countId, product.id, units * 1000L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = mapError(e)
            }
        }
    }

    fun approve() {
        launchGuarded { _approval.value = service.approve(countId, userIdProvider()) }
    }

    fun deleteDraft() {
        launchGuarded {
            service.deleteDraft(countId)
            _deleted.value = true
        }
    }

    fun consumeError() {
        _error.value = null
    }

    fun consumeApproval() {
        _approval.value = null
    }

    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = mapError(e)
            }
        }
    }

    private fun mapError(e: Exception): CountScreenError = when (e) {
        is CountException.InvalidQuantity -> CountScreenError.INVALID
        is CountException.ItemNotInCount -> CountScreenError.NOT_IN_COUNT
        else -> CountScreenError.UNKNOWN
    }

    companion object {
        fun factory(countId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                StockCountViewModel(app.stockCountService, app.productRepository, app.database.stockCounts(), countId, app::activeUserId)
            }
        }
    }
}
