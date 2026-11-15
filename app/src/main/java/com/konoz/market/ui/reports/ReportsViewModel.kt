package com.konoz.market.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.Product
import com.konoz.market.domain.CategorySales
import com.konoz.market.domain.ExpenseByCategory
import com.konoz.market.domain.HourStat
import com.konoz.market.domain.PeriodKind
import com.konoz.market.domain.PeriodRange
import com.konoz.market.domain.PeriodSummary
import com.konoz.market.domain.Periods
import com.konoz.market.domain.ProductPerformance
import com.konoz.market.domain.ReportCsv
import com.konoz.market.domain.ReportService
import com.konoz.market.domain.WeekdayStat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ReportPeriod { TODAY, MONTH, YEAR, LAST_30_DAYS }

data class ReportsUi(
    val period: ReportPeriod = ReportPeriod.TODAY,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val summary: PeriodSummary? = null,
    /** The previous day / month / year; null for "last 30 days". */
    val previous: PeriodSummary? = null,
    val topQty: List<ProductPerformance> = emptyList(),
    val topProfit: List<ProductPerformance> = emptyList(),
    val leastSold: List<ProductPerformance> = emptyList(),
    val nonMoving: List<Product> = emptyList(),
    val categories: List<CategorySales> = emptyList(),
    val expenses: List<ExpenseByCategory> = emptyList(),
    val hours: List<HourStat> = emptyList(),
    val weekdays: List<WeekdayStat> = emptyList(),
)

class ReportsViewModel(
    private val service: ReportService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _ui = MutableStateFlow(ReportsUi())
    val ui: StateFlow<ReportsUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        load(ReportPeriod.TODAY)
    }

    fun setPeriod(p: ReportPeriod) {
        if (p == _ui.value.period && !_ui.value.failed) return
        load(p)
    }

    fun refresh() = load(_ui.value.period)

    /**
     * The CSV file text (with BOM) of the period on screen, or null while nothing is loaded. The product list is the full
     * one (not the top-5 shown on screen). [includeProfit] = false leaves out costs and profits.
     */
    suspend fun csvText(includeProfit: Boolean, formatDate: (Long) -> String): String? {
        val state = _ui.value
        val summary = state.summary ?: return null
        val products = service.productPerformance(summary.range)
        return ReportCsv.BOM + ReportCsv.build(summary, products, state.categories, state.expenses, includeProfit, formatDate)
    }

    private fun load(p: ReportPeriod) {
        job?.cancel()
        _ui.value = _ui.value.copy(period = p, loading = true, failed = false)
        job = viewModelScope.launch {
            try {
                val now = clock()
                val range = rangeOf(p, now)
                val kind = when (p) {
                    ReportPeriod.TODAY -> PeriodKind.DAY
                    ReportPeriod.MONTH -> PeriodKind.MONTH
                    ReportPeriod.YEAR -> PeriodKind.YEAR
                    ReportPeriod.LAST_30_DAYS -> null
                }
                val comparison = kind?.let { service.compare(it, now) }
                _ui.value = ReportsUi(
                    period = p,
                    loading = false,
                    summary = comparison?.current ?: service.summary(range),
                    previous = comparison?.previous,
                    topQty = service.topByQuantity(range, 5),
                    topProfit = service.topByProfit(range, 5),
                    leastSold = service.leastSold(range, 5),
                    nonMoving = service.nonMoving(range).take(10),
                    categories = service.salesByCategory(range),
                    expenses = service.expensesByCategory(range),
                    hours = service.peakHours(range),
                    weekdays = service.weekdayComparison(range),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, failed = true)
            }
        }
    }

    private fun rangeOf(p: ReportPeriod, now: Long): PeriodRange = when (p) {
        ReportPeriod.TODAY -> Periods.rangeOf(PeriodKind.DAY, now)
        ReportPeriod.MONTH -> Periods.rangeOf(PeriodKind.MONTH, now)
        ReportPeriod.YEAR -> Periods.rangeOf(PeriodKind.YEAR, now)
        ReportPeriod.LAST_30_DAYS -> {
            val start = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, -29) }.timeInMillis
            PeriodRange(Periods.rangeOf(PeriodKind.DAY, start).from, Periods.rangeOf(PeriodKind.DAY, now).to)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ReportsViewModel(app.reportService)
            }
        }
    }
}
