package com.konoz.market.domain

import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.dao.QtyCostRow
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.TimeZone

/**
 * Financial summary of one period. All money in piastres.
 *
 *  grossSales          = total of completed invoices (after discounts)
 *  netSales            = grossSales - returnsTotal
 *  cogs                = cost snapshots of the sold invoices - cost of RESELLABLE returned units
 *  grossProfit         = netSales - cogs
 *  inventoryLosses     = damaged / expired / negative adjustments / count shortages, valued at cost
 *  estimatedNetProfit  = grossProfit - expenses - inventoryLosses
 * Gross sales are never profit.
 */
data class PeriodSummary(
    val range: PeriodRange,
    val grossSales: Long,
    val discounts: Long,
    val taxCollected: Long,
    val invoiceCount: Int,
    val averageInvoice: Long,
    val returnsTotal: Long,
    val netSales: Long,
    val cogs: Long,
    val grossProfit: Long,
    val expenses: Long,
    val inventoryLosses: Long,
    val estimatedNetProfit: Long,
)

data class DayPoint(val dayStart: Long, val grossSales: Long, val netSales: Long, val invoiceCount: Int)

data class PeriodComparison(val current: PeriodSummary, val previous: PeriodSummary)

/** Read-only reporting over the local database. Every call reads inside one transaction so the numbers are consistent. */
class ReportService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: TimeZone = TimeZone.getDefault(),
) {
    private val reports get() = db.reports()

    suspend fun summary(range: PeriodRange): PeriodSummary = db.tx { summaryIn(range) }

    suspend fun summary(kind: PeriodKind, at: Long = clock()): PeriodSummary = summary(Periods.rangeOf(kind, at, zone))

    /** The period that contains [at] against the previous day / month / year. */
    suspend fun compare(kind: PeriodKind, at: Long = clock()): PeriodComparison = db.tx {
        val current = summaryIn(Periods.rangeOf(kind, at, zone))
        val previous = summaryIn(Periods.rangeOf(kind, shifted(at, kind, -1), zone))
        PeriodComparison(current, previous)
    }

    /** One point per calendar day, oldest first, ending with the day that contains [endAt]. */
    suspend fun dailySeries(days: Int = 7, endAt: Long = clock()): List<DayPoint> = db.tx {
        require(days > 0)
        (days - 1 downTo 0).map { back ->
            val cal = Calendar.getInstance(zone).apply { timeInMillis = endAt; add(Calendar.DAY_OF_MONTH, -back) }
            val r = Periods.rangeOf(PeriodKind.DAY, cal.timeInMillis, zone)
            val agg = reports.salesAggregate(r.from, r.to)
            DayPoint(r.from, agg.gross, agg.gross - reports.returnsTotal(r.from, r.to), agg.invoiceCount)
        }
    }

    // ---------- internals ----------
    private suspend fun summaryIn(range: PeriodRange): PeriodSummary {
        val agg = reports.salesAggregate(range.from, range.to)
        val returns = reports.returnsTotal(range.from, range.to)
        val returnedCost = valueOf(reports.resellableReturnRows(range.from, range.to))
        val losses = valueOf(reports.lossRows(range.from, range.to))
        val expenses = reports.expensesTotal(range.from, range.to)

        val netSales = agg.gross - returns
        val cogs = agg.soldCost - returnedCost
        val grossProfit = netSales - cogs
        return PeriodSummary(
            range = range,
            grossSales = agg.gross,
            discounts = agg.discounts,
            taxCollected = agg.tax,
            invoiceCount = agg.invoiceCount,
            averageInvoice = average(agg.gross, agg.invoiceCount),
            returnsTotal = returns,
            netSales = netSales,
            cogs = cogs,
            grossProfit = grossProfit,
            expenses = expenses,
            inventoryLosses = losses,
            estimatedNetProfit = grossProfit - expenses - losses,
        )
    }

    private fun valueOf(rows: List<QtyCostRow>): Long = rows.sumOf { Money.costOf(it.unitCost, it.qtyMilli) }

    private fun average(total: Long, count: Int): Long =
        if (count == 0) 0L else BigDecimal(total).divide(BigDecimal(count), 0, RoundingMode.HALF_UP).longValueExact()

    private fun shifted(at: Long, kind: PeriodKind, amount: Int): Long {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = at }
        when (kind) {
            PeriodKind.DAY -> cal.add(Calendar.DAY_OF_MONTH, amount)
            PeriodKind.MONTH -> cal.add(Calendar.MONTH, amount)
            PeriodKind.YEAR -> cal.add(Calendar.YEAR, amount)
        }
        return cal.timeInMillis
    }
}
