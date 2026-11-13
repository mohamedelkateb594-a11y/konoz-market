package com.konoz.market.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Shift
import com.konoz.market.data.entity.ShiftStatus
import com.konoz.market.domain.ShiftException
import com.konoz.market.domain.ShiftService
import com.konoz.market.domain.ShiftSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ShiftError { AMOUNT, ALREADY_OPEN, NOT_OPEN, UNKNOWN }

/** What the top card shows: [Loading] until the first read, then either no shift or the live summary of the open one. */
sealed interface OpenShiftState {
    data object Loading : OpenShiftState
    data object None : OpenShiftState
    data class Open(val summary: ShiftSummary) : OpenShiftState
}

class ShiftViewModel(
    private val service: ShiftService,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    private fun stateOf(s: ShiftSummary?): OpenShiftState = if (s == null) OpenShiftState.None else OpenShiftState.Open(s)

    val openShift: StateFlow<OpenShiftState> = service.observeLiveSummary()
        .map { stateOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpenShiftState.Loading)

    /** Closed shifts, newest first. */
    val history: StateFlow<List<Shift>> = service.observeAll()
        .map { all -> all.filter { it.status == ShiftStatus.CLOSED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Text of the "open shift" dialog; null = dialog hidden. */
    private val _openForm = MutableStateFlow<String?>(null)
    val openForm: StateFlow<String?> = _openForm.asStateFlow()

    /** Text of the "counted cash" field of the close dialog; null = dialog hidden. */
    private val _closeForm = MutableStateFlow<String?>(null)
    val closeForm: StateFlow<String?> = _closeForm.asStateFlow()

    /** The shift report being shown (a closed shift picked from the history, or the one that was just closed). */
    private val _detail = MutableStateFlow<ShiftSummary?>(null)
    val detail: StateFlow<ShiftSummary?> = _detail.asStateFlow()

    private val _error = MutableStateFlow<ShiftError?>(null)
    val error: StateFlow<ShiftError?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun showOpenForm() {
        _error.value = null
        _openForm.value = ""
    }

    fun editOpenForm(text: String) {
        _error.value = null
        _openForm.value = text
    }

    fun dismissOpenForm() {
        _openForm.value = null
    }

    fun showCloseForm() {
        _error.value = null
        _closeForm.value = ""
    }

    fun editCloseForm(text: String) {
        _error.value = null
        _closeForm.value = text
    }

    fun dismissCloseForm() {
        _closeForm.value = null
    }

    fun dismissDetail() {
        _detail.value = null
    }

    fun showDetail(shiftId: Long) {
        viewModelScope.launch {
            try {
                _detail.value = service.summary(shiftId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = ShiftError.UNKNOWN
            }
        }
    }

    /** Opens a shift. A blank opening cash means zero. */
    fun open() {
        val text = _openForm.value ?: return
        if (_saving.value) return
        val amount = if (text.isBlank()) 0L else Money.parseToMinor(text)
        if (amount == null || amount < 0L) {
            _error.value = ShiftError.AMOUNT
            return
        }
        _saving.value = true
        viewModelScope.launch {
            try {
                service.open(amount, userIdProvider())
                _openForm.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ShiftException) {
                _error.value = errorOf(e)
            } catch (e: Exception) {
                _error.value = ShiftError.UNKNOWN
            } finally {
                _saving.value = false
            }
        }
    }

    /** Closes the open shift with the counted cash, then shows its final report. */
    fun close() {
        val text = _closeForm.value ?: return
        val open = openShift.value as? OpenShiftState.Open ?: return
        if (_saving.value) return
        val amount = Money.parseToMinor(text)
        if (amount == null || amount < 0L) {
            _error.value = ShiftError.AMOUNT
            return
        }
        _saving.value = true
        viewModelScope.launch {
            try {
                val result = service.close(open.summary.shift.id, amount, userIdProvider())
                _closeForm.value = null
                _detail.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: ShiftException) {
                _error.value = errorOf(e)
            } catch (e: Exception) {
                _error.value = ShiftError.UNKNOWN
            } finally {
                _saving.value = false
            }
        }
    }

    private fun errorOf(e: ShiftException): ShiftError = when (e) {
        is ShiftException.InvalidAmount -> ShiftError.AMOUNT
        is ShiftException.AlreadyOpen -> ShiftError.ALREADY_OPEN
        is ShiftException.NotFound -> ShiftError.NOT_OPEN
        is ShiftException.AlreadyClosed -> ShiftError.NOT_OPEN
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ShiftViewModel(app.shiftService, app::activeUserId)
            }
        }
    }
}
