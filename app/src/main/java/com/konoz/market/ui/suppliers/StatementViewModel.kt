package com.konoz.market.ui.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Money
import com.konoz.market.data.entity.PurchaseStatus
import com.konoz.market.data.entity.Supplier
import com.konoz.market.data.repo.CatalogRepository
import com.konoz.market.domain.PurchaseException
import com.konoz.market.domain.PurchaseService
import com.konoz.market.domain.StatementLine
import com.konoz.market.domain.SupplierStatement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StatementNotice { SETTLED, INVALID_AMOUNT, FAILED }

class StatementViewModel(
    catalog: CatalogRepository,
    private val service: PurchaseService,
    private val supplierId: Long,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    private val _supplier = MutableStateFlow<Supplier?>(null)
    val supplier: StateFlow<Supplier?> = _supplier.asStateFlow()

    val lines: StateFlow<List<StatementLine>> = catalog.observeSupplierInvoices(supplierId)
        .map { list -> SupplierStatement.build(list.filter { it.status == PurchaseStatus.APPROVED }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val balance: StateFlow<Long> = lines.map { it.lastOrNull()?.balanceAfter ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _notice = MutableStateFlow<StatementNotice?>(null)
    val notice: StateFlow<StatementNotice?> = _notice.asStateFlow()

    init {
        viewModelScope.launch { _supplier.value = catalog.getSupplier(supplierId) }
    }

    fun settle(invoiceId: Long, amountText: String) {
        val amount = Money.parseToMinor(amountText)
        if (amount == null) {
            _notice.value = StatementNotice.INVALID_AMOUNT
            return
        }
        viewModelScope.launch {
            try {
                service.settle(invoiceId, amount, userIdProvider())
                _notice.value = StatementNotice.SETTLED
            } catch (e: CancellationException) {
                throw e
            } catch (e: PurchaseException) {
                _notice.value = StatementNotice.INVALID_AMOUNT
            } catch (e: Exception) {
                _notice.value = StatementNotice.FAILED
            }
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    companion object {
        fun factory(supplierId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                StatementViewModel(app.catalogRepository, app.purchaseService, supplierId, app::activeUserId)
            }
        }
    }
}
