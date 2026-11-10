package com.konoz.market.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Product
import com.konoz.market.data.seed.SettingKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AlertTab { LOW, OUT, EXPIRING, EXPIRED }

/** A product that needs restocking with a suggested purchase quantity (milli): back up to twice its alert level. */
data class ProductAlert(val product: Product, val suggestedMilli: Long)

data class BatchAlert(val productName: String, val qtyMilli: Long, val expiry: Long, val days: Int)

object AlertMath {
    const val DAY_MS = 86_400_000L

    /** Suggested purchase quantity: enough to reach twice the alert level, never less than one unit. */
    fun suggestedMilli(currentMilli: Long, thresholdMilli: Long): Long = maxOf(2 * thresholdMilli - currentMilli, 1000L)
}

class StockAlertsViewModel(private val db: KonozDatabase) : ViewModel() {

    private val now = System.currentTimeMillis()

    private val _tab = MutableStateFlow(AlertTab.LOW)
    val tab: StateFlow<AlertTab> = _tab.asStateFlow()

    private val _days = MutableStateFlow(30)

    val low: StateFlow<List<ProductAlert>> = db.products().observeLowStock()
        .map { list -> list.map { ProductAlert(it, AlertMath.suggestedMilli(it.currentQtyMilli, it.lowStockThresholdMilli)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val out: StateFlow<List<ProductAlert>> = db.products().observeOutOfStock()
        .map { list -> list.map { ProductAlert(it, AlertMath.suggestedMilli(it.currentQtyMilli, it.lowStockThresholdMilli)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expiring: StateFlow<List<BatchAlert>> = _days.flatMapLatest { d ->
        combine(db.stock().observeExpiringSoon(now, now + d * AlertMath.DAY_MS), db.products().observeAll()) { batches, products ->
            val byId = products.associateBy { it.id }
            batches.mapNotNull { b ->
                val expiry = b.expiryDate ?: return@mapNotNull null
                byId[b.productId]?.let { BatchAlert(it.name, b.qtyMilli, expiry, ((expiry - now) / AlertMath.DAY_MS).toInt()) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expired: StateFlow<List<BatchAlert>> = combine(db.stock().observeExpired(now), db.products().observeAll()) { batches, products ->
        val byId = products.associateBy { it.id }
        batches.mapNotNull { b ->
            val expiry = b.expiryDate ?: return@mapNotNull null
            byId[b.productId]?.let { BatchAlert(it.name, b.qtyMilli, expiry, ((now - expiry) / AlertMath.DAY_MS).toInt()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            db.settings().get(SettingKeys.EXPIRY_ALERT_DAYS)?.toIntOrNull()?.let { if (it > 0) _days.value = it }
        }
    }

    fun setTab(t: AlertTab) {
        _tab.value = t
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                StockAlertsViewModel(app.database)
            }
        }
    }
}
