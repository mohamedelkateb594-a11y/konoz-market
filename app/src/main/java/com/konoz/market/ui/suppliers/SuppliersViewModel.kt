package com.konoz.market.ui.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.Supplier
import com.konoz.market.data.repo.CatalogRepository
import com.konoz.market.domain.SupplierBalances
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SupplierRow(val supplier: Supplier, val balance: Long)

enum class SuppliersNotice { DELETE_BLOCKED, SAVE_FAILED }

class SuppliersViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val rows: StateFlow<List<SupplierRow>> = combine(
        catalog.observeSuppliers(), catalog.observeAllInvoices(), _query,
    ) { suppliers, invoices, q ->
        val balances = SupplierBalances.of(invoices)
        val needle = q.trim()
        suppliers
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) || it.phone.orEmpty().contains(needle) }
            .map { SupplierRow(it, balances[it.id] ?: 0L) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<SuppliersNotice?>(null)
    val notice: StateFlow<SuppliersNotice?> = _notice.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun save(id: Long, name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            try {
                catalog.saveSupplier(id, name, phone, address, notes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = SuppliersNotice.SAVE_FAILED
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            if (!catalog.deleteSupplier(id)) _notice.value = SuppliersNotice.DELETE_BLOCKED
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                SuppliersViewModel(app.catalogRepository)
            }
        }
    }
}
