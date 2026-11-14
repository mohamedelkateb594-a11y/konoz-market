package com.konoz.market.ui.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.domain.InvoiceData
import com.konoz.market.domain.InvoiceLoader
import com.konoz.market.domain.PhoneNumbers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ShareMessage { INVALID_PHONE, NO_WHATSAPP, FAILED }

data class InvoicesUi(
    val query: String = "",
    val recent: List<SalesInvoice> = emptyList(),
    val selected: InvoiceData? = null,
    /** What was typed, as typed; converted only when sending. */
    val phone: String = "",
    val busy: Boolean = false,
    val message: ShareMessage? = null,
) {
    val shown: List<SalesInvoice>
        get() = if (query.isBlank()) recent else recent.filter { it.number?.toString()?.contains(query.trim()) == true }
}

class InvoicesViewModel(private val app: KonozApp) : ViewModel() {
    private val loader = InvoiceLoader(app.database)
    private val sharer = InvoiceSharer(app)

    private val _ui = MutableStateFlow(InvoicesUi())
    val ui: StateFlow<InvoicesUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.database.sales().observeRecent(RECENT_LIMIT).collect { list -> _ui.update { it.copy(recent = list) } }
        }
    }

    fun onQuery(text: String) = _ui.update { it.copy(query = text.filter(Char::isDigit)) }

    fun open(invoiceId: Long) {
        viewModelScope.launch {
            try {
                loader.load(invoiceId)?.let { data -> _ui.update { it.copy(selected = data, phone = "", message = null) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = ShareMessage.FAILED) }
            }
        }
    }

    fun close() = _ui.update { it.copy(selected = null, phone = "", message = null) }

    fun onPhone(text: String) = _ui.update { it.copy(phone = text.take(20), message = null) }

    /** Sends the PDF to the typed customer number on WhatsApp. */
    fun sendWhatsApp() = share(InvoiceFormat.PDF, withWhatsApp = true)

    fun sharePdf() = share(InvoiceFormat.PDF, withWhatsApp = false)

    fun shareImage() = share(InvoiceFormat.IMAGE, withWhatsApp = false)

    private fun share(format: InvoiceFormat, withWhatsApp: Boolean) {
        val state = _ui.value
        val data = state.selected ?: return
        if (state.busy) return
        val number = if (withWhatsApp) {
            PhoneNumbers.toWhatsApp(state.phone) ?: run {
                _ui.update { it.copy(message = ShareMessage.INVALID_PHONE) }
                return
            }
        } else {
            null
        }
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { sharer.createFile(data, format) }
                val result = sharer.share(file, format, sharer.caption(data), number)
                if (result == ShareResult.CHOOSER_NO_WHATSAPP) _ui.update { it.copy(message = ShareMessage.NO_WHATSAPP) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = ShareMessage.FAILED) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        private const val RECENT_LIMIT = 200

        val Factory = viewModelFactory {
            initializer { InvoicesViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
