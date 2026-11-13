package com.konoz.market.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Shift
import com.konoz.market.domain.ShiftException
import com.konoz.market.domain.ShiftReport
import com.konoz.market.domain.ShiftService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface ShiftMessage {
    data object Opened : ShiftMessage
    /** [difference] = counted - expected (negative = shortage). */
    data class Closed(val difference: Long) : ShiftMessage
    data object AlreadyOpen : ShiftMessage
    data object NoOpenShift : ShiftMessage
    data object InvalidAmount : ShiftMessage
    data object Failed : ShiftMessage
}

data class ShiftUi(
    val loaded: Boolean = false,
    val current: ShiftReport? = null,
    val history: List<Shift> = emptyList(),
    /** The report being viewed from the history list. */
    val selected: ShiftReport? = null,
    val message: ShiftMessage? = null,
)

class ShiftViewModel(
    private val service: ShiftService,
    db: KonozDatabase,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShiftUi())
    val ui: StateFlow<ShiftUi> = _ui.asStateFlow()
    private var selectedId: Long? = null

    init {
        // Keeps the live drawer figures current while sales, returns and expenses are being recorded.
        viewModelScope.launch {
            combine(
                db.shifts().observeAll(),
                db.sales().observeRecent(1),
                db.sales().observeReturns(),
                db.expenses().observeTotalBetween(0, Long.MAX_VALUE),
            ) { shifts, _, _, _ -> shifts }.collect { shifts ->
                try {
                    val selected = selectedId?.let { service.report(it) }
                    _ui.value = _ui.value.copy(loaded = true, current = service.currentReport(), history = shifts.filter { !it.isOpen() }, selected = selected)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.value = _ui.value.copy(loaded = true, message = ShiftMessage.Failed)
                }
            }
        }
    }

    private fun Shift.isOpen() = closedAt == null

    fun open(cashText: String) = run(cashText) { amount, userId ->
        service.open(amount, userId)
        ShiftMessage.Opened
    }

    fun close(cashText: String) = run(cashText) { amount, userId ->
        ShiftMessage.Closed(service.close(amount, userId).difference ?: 0L)
    }

    fun select(shiftId: Long) {
        selectedId = shiftId
        viewModelScope.launch {
            try {
                _ui.value = _ui.value.copy(selected = service.report(shiftId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                selectedId = null
            }
        }
    }

    fun clearSelection() {
        selectedId = null
        _ui.value = _ui.value.copy(selected = null)
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun run(cashText: String, block: suspend (Long, Long) -> ShiftMessage) {
        val amount = Money.parseToMinor(cashText.ifBlank { "0" })
        if (amount == null) {
            _ui.value = _ui.value.copy(message = ShiftMessage.InvalidAmount)
            return
        }
        viewModelScope.launch {
            val message = try {
                block(amount, userIdProvider())
            } catch (e: CancellationException) {
                throw e
            } catch (e: ShiftException) {
                when (e) {
                    is ShiftException.AlreadyOpen -> ShiftMessage.AlreadyOpen
                    is ShiftException.NoOpenShift -> ShiftMessage.NoOpenShift
                    is ShiftException.InvalidAmount -> ShiftMessage.InvalidAmount
                    is ShiftException.NotFound -> ShiftMessage.Failed
                }
            } catch (e: Exception) {
                ShiftMessage.Failed
            }
            _ui.value = _ui.value.copy(message = message)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ShiftViewModel(app.shiftService, app.database, app::activeUserId)
            }
        }
    }
}
