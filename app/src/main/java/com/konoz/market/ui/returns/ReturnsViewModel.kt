package com.konoz.market.ui.returns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.ReturnCondition
import com.konoz.market.domain.ReturnDraft
import com.konoz.market.domain.ReturnException
import com.konoz.market.domain.ReturnLine
import com.konoz.market.domain.ReturnPreview
import com.konoz.market.domain.ReturnResult
import com.konoz.market.domain.ReturnService
import com.konoz.market.domain.ReturnableInvoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class ReturnsError { EMPTY, EXCEEDS, INVALID, UNKNOWN }

data class Pick(val qty: String = "", val damaged: Boolean = false)

data class ReturnsUiState(
    val number: String = "",
    val invoice: ReturnableInvoice? = null,
    val notFound: Boolean = false,
    val picks: Map<Long, Pick> = emptyMap(),
    val reason: String = "",
    val saving: Boolean = false,
    val error: ReturnsError? = null,
    val done: ReturnResult? = null,
) {
    fun pickedMilli(): Map<Long, Long> = picks.mapNotNull { (id, p) ->
        Quantities.parseMilli(p.qty)?.takeIf { it > 0L }?.let { id to it }
    }.toMap()

    fun refundPreview(): Long = invoice?.let { ReturnPreview.refund(it, pickedMilli()) } ?: 0L
}

class ReturnsViewModel(
    private val service: ReturnService,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    /** One uuid per screen: confirming twice can never refund or restock twice. */
    private val uuid = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(ReturnsUiState())
    val state: StateFlow<ReturnsUiState> = _state.asStateFlow()

    fun setNumber(text: String) {
        _state.update { it.copy(number = text, notFound = false) }
    }

    fun find() {
        val number = _state.value.number.trim().toLongOrNull()
        if (number == null) {
            _state.update { it.copy(invoice = null, notFound = true) }
            return
        }
        viewModelScope.launch {
            val inv = service.loadForReturn(number)
            _state.update { it.copy(invoice = inv, notFound = inv == null, picks = emptyMap(), error = null) }
        }
    }

    fun setQty(itemId: Long, text: String) = editPick(itemId) { it.copy(qty = text) }

    fun setDamaged(itemId: Long, damaged: Boolean) = editPick(itemId) { it.copy(damaged = damaged) }

    fun setReason(text: String) {
        _state.update { it.copy(reason = text, error = null) }
    }

    private fun editPick(itemId: Long, f: (Pick) -> Pick) {
        _state.update { it.copy(picks = it.picks + (itemId to f(it.picks[itemId] ?: Pick())), error = null) }
    }

    fun submit() {
        val s = _state.value
        val invoice = s.invoice ?: return
        val picked = s.pickedMilli()
        val lines = invoice.lines.mapNotNull { l ->
            val q = picked[l.item.id] ?: return@mapNotNull null
            ReturnLine(l.item.id, q, if (s.picks[l.item.id]?.damaged == true) ReturnCondition.DAMAGED else ReturnCondition.RESELLABLE)
        }
        if (lines.isEmpty()) {
            _state.update { it.copy(error = ReturnsError.EMPTY) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val result = service.createReturn(ReturnDraft(uuid, invoice.invoice.id, userIdProvider(), lines, s.reason.trim().ifEmpty { null }))
                _state.update { it.copy(saving = false, done = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReturnException) {
                val err = when (e) {
                    is ReturnException.QuantityExceeds -> ReturnsError.EXCEEDS
                    is ReturnException.InvalidQuantity -> ReturnsError.INVALID
                    is ReturnException.EmptyReturn -> ReturnsError.EMPTY
                    else -> ReturnsError.UNKNOWN
                }
                _state.update { it.copy(saving = false, error = err) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = ReturnsError.UNKNOWN) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ReturnsViewModel(app.returnService, app::activeUserId)
            }
        }
    }
}
