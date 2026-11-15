package com.konoz.market.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.seed.SettingKeys
import com.konoz.market.domain.DayPoint
import com.konoz.market.domain.PeriodKind
import com.konoz.market.domain.PeriodSummary
import com.konoz.market.domain.ReportService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUi(
    /** Today. Null until the first load finishes. */
    val today: PeriodSummary? = null,
    /** Last 7 days, oldest first. */
    val series: List<DayPoint> = emptyList(),
    val inventoryValue: Long = 0,
    val lowStockCount: Int = 0,
    /** Batches that expire within the alert window (setting, 30 days by default). */
    val expiringBatches: Int = 0,
    val recent: List<SalesInvoice> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val db: KonozDatabase,
    private val service: ReportService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val expiryDays = MutableStateFlow(30)

    init {
        viewModelScope.launch {
            db.settings().get(SettingKeys.EXPIRY_ALERT_DAYS)?.toIntOrNull()?.let { if (it > 0) expiryDays.value = it }
        }
    }

    /** Re-reads the report whenever sales, returns or expenses change, always for the current day. */
    private val report: Flow<Pair<PeriodSummary, List<DayPoint>>> = combine(
        db.sales().observeRecent(1),
        db.sales().observeReturns(),
        db.expenses().observeTotalBetween(0, Long.MAX_VALUE),
    ) { _, _, _ -> }.mapLatest {
        val now = clock()
        service.summary(PeriodKind.DAY, now) to service.dailySeries(7, now)
    }

    private val expiring: Flow<Int> = expiryDays.flatMapLatest { d ->
        val now = clock()
        db.stock().observeExpiringSoon(now, now + d * DAY_MS).map { it.size }
    }

    val ui: StateFlow<DashboardUi> = combine(
        report,
        db.products().observeInventoryValue(),
        db.products().observeLowStockCount(),
        expiring,
        db.sales().observeRecent(5),
    ) { (today, series), value, low, expiringCount, recent ->
        DashboardUi(today, series, value, low, expiringCount, recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())

    companion object {
        private const val DAY_MS = 86_400_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                DashboardViewModel(app.database, app.reportService)
            }
        }
    }
}
