package com.konoz.market.ui.stockops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.Product
import com.konoz.market.data.repo.ProductRepository
import com.konoz.market.domain.StockAdjustmentService
import com.konoz.market.domain.StockException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class StockOp { ADJUST, DAMAGED, EXPIRED, CONVERT }

enum class OpsError { INVALID, REASON, STOCK, SAME, PICK, UNKNOWN }

data class StockOpsState(
    val op: StockOp = StockOp.ADJUST,
    val product: Product? = null,
    val target: Product? = null,
    val increase: Boolean = true,
    val qty: String = "",
    val targetQty: String = "",
    val reason: String = "",
    val saving: Boolean = false,
    val error: OpsError? = null,
    val doneCost: Long? = null,
)

class StockOpsViewModel(
    private val repo: ProductRepository,
    private val service: StockAdjustmentService,
    private val userIdProvider: suspend () -> Long,
) : ViewModel() {

    /** Replaced after every successful operation; a failed attempt can be retried with the same key. */
    private var uuid = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(StockOpsState())
    val state: StateFlow<StockOpsState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Product>> = _query
        .debounce(150)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.observeProducts(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setOp(op: StockOp) = edit { it.copy(op = op) }
    fun setIncrease(v: Boolean) = edit { it.copy(increase = v) }
    fun setQty(t: String) = edit { it.copy(qty = t) }
    fun setTargetQty(t: String) = edit { it.copy(targetQty = t) }
    fun setReason(t: String) = edit { it.copy(reason = t) }
    fun clearProduct() = edit { it.copy(product = null) }
    fun clearTarget() = edit { it.copy(target = null) }

    fun pick(p: Product) {
        _query.value = ""
        edit { it.copy(product = p) }
    }

    fun pickTarget(p: Product) {
        _query.value = ""
        edit { it.copy(target = p) }
    }

    fun consumeDone() {
        _state.update { it.copy(doneCost = null) }
    }

    private fun edit(f: (StockOpsState) -> StockOpsState) {
        _state.update { f(it).copy(error = null) }
    }

    private fun fail(e: OpsError) {
        _state.update { it.copy(saving = false, error = e) }
    }

    fun submit() {
        val s = _state.value
        val source = s.product
        if (source == null) {
            fail(OpsError.PICK)
            return
        }
        val qty = Quantities.parseMilli(s.qty)?.takeIf { it > 0L }
        if (qty == null) {
            fail(OpsError.INVALID)
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val userId = userIdProvider()
                val result = when (s.op) {
                    StockOp.ADJUST -> service.adjust(uuid, source.id, if (s.increase) qty else -qty, s.reason, userId)
                    StockOp.DAMAGED -> service.writeOff(uuid, source.id, qty, MovementType.DAMAGED, s.reason.ifBlank { null }, userId)
                    StockOp.EXPIRED -> service.writeOff(uuid, source.id, qty, MovementType.EXPIRED, s.reason.ifBlank { null }, userId)
                    StockOp.CONVERT -> {
                        val target = s.target
                        val targetQty = Quantities.parseMilli(s.targetQty)?.takeIf { it > 0L }
                        if (target == null) {
                            fail(OpsError.PICK)
                            return@launch
                        }
                        if (targetQty == null) {
                            fail(OpsError.INVALID)
                            return@launch
                        }
                        service.convertUnits(uuid, source.id, qty, target.id, targetQty, userId)
                    }
                }
                uuid = UUID.randomUUID().toString()
                _state.update { StockOpsState(op = it.op, doneCost = result.costValue) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StockException) {
                fail(
                    when (e) {
                        is StockException.ReasonRequired -> OpsError.REASON
                        is StockException.InsufficientStock -> OpsError.STOCK
                        is StockException.InvalidQuantity -> OpsError.INVALID
                        is StockException.SameProduct -> OpsError.SAME
                        is StockException.ProductNotFound -> OpsError.UNKNOWN
                    }
                )
            } catch (e: Exception) {
                fail(OpsError.UNKNOWN)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                StockOpsViewModel(app.productRepository, app.stockAdjustmentService, app::activeUserId)
            }
        }
    }
}
