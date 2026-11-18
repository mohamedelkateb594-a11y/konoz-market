package com.konoz.market.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.Customer
import com.konoz.market.domain.CustomerError
import com.konoz.market.domain.CustomerException
import com.konoz.market.domain.CustomerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add/edit form. [id] 0 = a new customer; [phone] is what is typed (shown the way people write numbers). */
data class CustomerEditor(
    val id: Long = 0,
    val name: String = "",
    val phone: String = "",
    val error: CustomerError? = null,
)

data class CustomersUi(
    val query: String = "",
    val all: List<Customer> = emptyList(),
    val editor: CustomerEditor? = null,
    /** The customer waiting for the delete confirmation. */
    val deleting: Customer? = null,
    val failed: Boolean = false,
) {
    val shown: List<Customer>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return all
            return all.filter { it.name.contains(q, ignoreCase = true) || CustomerService.displayPhone(it.phone).contains(q) }
        }
}

class CustomersViewModel(app: KonozApp) : ViewModel() {
    private val service = CustomerService(app.database)

    private val _ui = MutableStateFlow(CustomersUi())
    val ui: StateFlow<CustomersUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            service.observeAll().collect { list -> _ui.update { it.copy(all = list) } }
        }
    }

    fun onQuery(text: String) = _ui.update { it.copy(query = text) }

    fun openNew() = _ui.update { it.copy(editor = CustomerEditor()) }

    fun openEdit(c: Customer) =
        _ui.update { it.copy(editor = CustomerEditor(id = c.id, name = c.name, phone = CustomerService.displayPhone(c.phone))) }

    fun onEditorName(text: String) =
        _ui.update { s -> s.copy(editor = s.editor?.copy(name = text.take(CustomerService.MAX_NAME), error = null)) }

    fun onEditorPhone(text: String) = _ui.update { s -> s.copy(editor = s.editor?.copy(phone = text.take(20), error = null)) }

    fun closeEditor() = _ui.update { it.copy(editor = null) }

    fun saveEditor() {
        val editor = _ui.value.editor ?: return
        viewModelScope.launch {
            try {
                if (editor.id == 0L) service.add(editor.name, editor.phone) else service.update(editor.id, editor.name, editor.phone)
                _ui.update { it.copy(editor = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CustomerException) {
                _ui.update { s -> s.copy(editor = s.editor?.copy(error = e.error)) }
            } catch (e: Exception) {
                _ui.update { s -> s.copy(editor = s.editor?.copy(error = CustomerError.FAILED)) }
            }
        }
    }

    fun askDelete(c: Customer) = _ui.update { it.copy(deleting = c) }

    fun cancelDelete() = _ui.update { it.copy(deleting = null) }

    fun confirmDelete() {
        val customer = _ui.value.deleting ?: return
        viewModelScope.launch {
            try {
                service.delete(customer.id)
                _ui.update { it.copy(deleting = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(deleting = null, failed = true) }
            }
        }
    }

    fun consumeFailed() = _ui.update { it.copy(failed = false) }

    companion object {
        val Factory = viewModelFactory {
            initializer { CustomersViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
