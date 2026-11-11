package com.konoz.market.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Expense
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.domain.ExpenseException
import com.konoz.market.domain.ExpenseInput
import com.konoz.market.domain.ExpenseService
import com.konoz.market.domain.PeriodKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ExpenseError { AMOUNT, DESCRIPTION, UNKNOWN }

/** The add/edit form. [uuid] is created once per form: saving twice (double tap, retry) can never add two expenses. */
data class ExpenseForm(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val amount: String = "",
    val category: ExpenseCategory = ExpenseCategory.MISC,
    val description: String = "",
    val notes: String = "",
    val paidInCash: Boolean = true,
    val receiptPath: String? = null,
)

class ExpensesViewModel(
    private val service: ExpenseService,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    private val _period = MutableStateFlow(PeriodKind.DAY)
    val period: StateFlow<PeriodKind> = _period.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val list: StateFlow<List<Expense>> = _period
        .flatMapLatest { service.observeList(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayTotal: StateFlow<Long> = service.observeTotal(PeriodKind.DAY).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val monthTotal: StateFlow<Long> = service.observeTotal(PeriodKind.MONTH).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val yearTotal: StateFlow<Long> = service.observeTotal(PeriodKind.YEAR).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val periodTotal: StateFlow<Long> = combine(_period, dayTotal, monthTotal, yearTotal) { p, d, m, y ->
        when (p) {
            PeriodKind.DAY -> d
            PeriodKind.MONTH -> m
            PeriodKind.YEAR -> y
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _form = MutableStateFlow<ExpenseForm?>(null)
    val form: StateFlow<ExpenseForm?> = _form.asStateFlow()

    private val _error = MutableStateFlow<ExpenseError?>(null)
    val error: StateFlow<ExpenseError?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun setPeriod(p: PeriodKind) {
        _period.value = p
    }

    fun openNew() {
        _error.value = null
        _form.value = ExpenseForm()
    }

    fun openEdit(e: Expense) {
        _error.value = null
        _form.value = ExpenseForm(
            id = e.id, uuid = e.uuid, amount = Money.fromMinor(e.amount).toPlainString(), category = e.category,
            description = e.description, notes = e.notes.orEmpty(), paidInCash = e.paidInCash, receiptPath = e.receiptPath,
        )
    }

    fun editForm(transform: (ExpenseForm) -> ExpenseForm) {
        _error.value = null
        _form.update { it?.let(transform) }
    }

    fun closeForm() {
        _form.value = null
    }

    fun save() {
        val f = _form.value ?: return
        if (_saving.value) return
        val amount = Money.parseToMinor(f.amount)
        if (amount == null || amount <= 0L) {
            _error.value = ExpenseError.AMOUNT
            return
        }
        if (f.description.isBlank()) {
            _error.value = ExpenseError.DESCRIPTION
            return
        }
        _saving.value = true
        viewModelScope.launch {
            try {
                val input = ExpenseInput(amount, f.category, f.description, f.notes, f.receiptPath, f.paidInCash, null, f.uuid)
                val userId = userIdProvider()
                if (f.id == 0L) service.add(input, userId) else service.update(f.id, input, userId)
                _form.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExpenseException) {
                _error.value = when (e) {
                    is ExpenseException.InvalidAmount -> ExpenseError.AMOUNT
                    is ExpenseException.DescriptionRequired -> ExpenseError.DESCRIPTION
                    is ExpenseException.NotFound -> ExpenseError.UNKNOWN
                }
            } catch (e: Exception) {
                _error.value = ExpenseError.UNKNOWN
            } finally {
                _saving.value = false
            }
        }
    }

    /** Deletes the expense (the screen asks for confirmation first) and its receipt picture file. */
    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                val receipt = service.delete(id, userIdProvider())
                if (receipt != null) withContext(Dispatchers.IO) { File(receipt).delete() }
                _form.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = ExpenseError.UNKNOWN
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ExpensesViewModel(app.expenseService, app::activeUserId)
            }
        }
    }
}
