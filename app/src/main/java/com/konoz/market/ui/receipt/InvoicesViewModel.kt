package com.konoz.market.ui.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.domain.CustomerError
import com.konoz.market.domain.CustomerException
import com.konoz.market.domain.CustomerService
import com.konoz.market.domain.InvoiceData
import com.konoz.market.domain.InvoiceLoader
import com.konoz.market.domain.PhoneNumbers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ShareMessage { INVALID_PHONE, NAME_REQUIRED, NO_WHATSAPP, FAILED }

data class InvoicesUi(
    val query: String = "",
    val recent: List<SalesInvoice> = emptyList(),
    val selected: InvoiceData? = null,
    /** Database id of [selected], needed to record the customer on it. */
    val selectedId: Long? = null,
    /** What was typed, as typed; converted only when sending. */
    val phone: String = "",
    /** The customer's name typed beside the phone; recorded on the invoice and saved with the phone in the customers list. */
    val name: String = "",
    val busy: Boolean = false,
    val message: ShareMessage? = null,
) {
    val shown: List<SalesInvoice>
        get() = if (query.isBlank()) recent else recent.filter { it.number?.toString()?.contains(query.trim()) == true }
}

class InvoicesViewModel(private val app: KonozApp) : ViewModel() {
    private val loader = InvoiceLoader(app.database)
    private val sharer = InvoiceSharer(app)
    private val customers = CustomerService(app.database)

    private val _ui = MutableStateFlow(InvoicesUi())
    val ui: StateFlow<InvoicesUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.database.sales().observeRecent(RECENT_LIMIT).collect { list -> _ui.update { it.copy(recent = list) } }
        }
    }

    fun onQuery(text: String) = _ui.update { it.copy(query = text.filter(Char::isDigit)) }

    /** Opens the invoice; when it was already sent to a customer, that customer's phone and name are filled in. */
    fun open(invoiceId: Long) {
        viewModelScope.launch {
            try {
                loader.load(invoiceId)?.let { data ->
                    _ui.update {
                        it.copy(
                            selected = data,
                            selectedId = invoiceId,
                            phone = data.customerPhone?.let(CustomerService::displayPhone).orEmpty(),
                            name = data.customerName.orEmpty(),
                            message = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = ShareMessage.FAILED) }
            }
        }
    }

    /** Opens the invoice with this number (used right after a sale, when only the number is known). Does nothing if it is not found. */
    fun openByNumber(number: Long) {
        viewModelScope.launch {
            try {
                val id = app.database.sales().observeRecent(20).first().firstOrNull { it.number == number }?.id
                if (id != null) open(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = ShareMessage.FAILED) }
            }
        }
    }

    fun close() = _ui.update { it.copy(selected = null, selectedId = null, phone = "", name = "", message = null) }

    /** A number that is already in the customers list fills in its name, unless a name was already typed. */
    fun onPhone(text: String) {
        val typed = text.take(20)
        _ui.update { it.copy(phone = typed, message = null) }
        viewModelScope.launch {
            val known = try {
                customers.findByPhone(typed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (known != null) _ui.update { if (it.phone == typed && it.name.isBlank()) it.copy(name = known.name) else it }
        }
    }

    fun onName(text: String) = _ui.update { it.copy(name = text.take(CustomerService.MAX_NAME), message = null) }

    /** Sends the PDF to the typed customer number on WhatsApp. The customer is saved to the list and the name goes on the invoice. */
    fun sendWhatsApp() = share(InvoiceFormat.PDF, withWhatsApp = true)

    fun sharePdf() = share(InvoiceFormat.PDF, withWhatsApp = false)

    fun shareImage() = share(InvoiceFormat.IMAGE, withWhatsApp = false)

    private fun share(format: InvoiceFormat, withWhatsApp: Boolean) {
        val state = _ui.value
        val data = state.selected ?: return
        val invoiceId = state.selectedId ?: return
        if (state.busy) return
        val number = if (withWhatsApp) {
            PhoneNumbers.toWhatsApp(state.phone) ?: run {
                _ui.update { it.copy(message = ShareMessage.INVALID_PHONE) }
                return
            }
        } else {
            null
        }
        if (withWhatsApp && state.name.isBlank()) {
            _ui.update { it.copy(message = ShareMessage.NAME_REQUIRED) }
            return
        }
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val shown = recordCustomer(invoiceId, data, state)
                val file = withContext(Dispatchers.IO) { sharer.createFile(shown, format) }
                val result = sharer.share(file, format, sharer.caption(shown), number)
                if (result == ShareResult.CHOOSER_NO_WHATSAPP) _ui.update { it.copy(message = ShareMessage.NO_WHATSAPP) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CustomerException) {
                val message = when (e.error) {
                    CustomerError.NAME_REQUIRED -> ShareMessage.NAME_REQUIRED
                    CustomerError.INVALID_PHONE -> ShareMessage.INVALID_PHONE
                    else -> ShareMessage.FAILED
                }
                _ui.update { it.copy(message = message) }
            } catch (e: Exception) {
                _ui.update { it.copy(message = ShareMessage.FAILED) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Saves what was typed (name, and phone when given) on the invoice and in the customers list, then reloads the invoice
     * so the file that is sent shows the customer's name. Nothing typed = the invoice as it is.
     */
    private suspend fun recordCustomer(invoiceId: Long, data: InvoiceData, state: InvoicesUi): InvoiceData {
        if (state.name.isBlank() && state.phone.isBlank()) return data
        customers.saveForInvoice(invoiceId, state.name, state.phone)
        val fresh = loader.load(invoiceId) ?: return data
        _ui.update { it.copy(selected = fresh) }
        return fresh
    }

    companion object {
        private const val RECENT_LIMIT = 200

        val Factory = viewModelFactory {
            initializer { InvoicesViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
