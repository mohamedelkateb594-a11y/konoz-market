package com.konoz.market.domain

import com.konoz.market.core.Allocation
import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.dao.QtyCostRow
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.data.entity.Product
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.TimeZone

/**
 * Financial summary of one period. All money in piastres.
 *
 * Tax is a liability, not revenue, so it is taken out of sales before profit is computed:
 *  grossSales          = total of completed invoices, tax included (what customers paid)
 *  taxCollected        = tax on those invoices; returnsTax = the tax part of refunds (refund x invoice tax / invoice total)
 *  netSales            = (grossSales - taxCollected) - (returnsTotal - returnsTax)   // revenue without tax
 *  cogs                = cost snapshots of the sold invoices - cost of RESELLABLE returned units
 *  grossProfit         = netSales - cogs
 *  inventoryLosses     = damaged / expired / negative adjustments / count shortages, valued at cost
 *  estimatedNetProfit  = grossProfit - expenses - inventoryLosses
 * Gross sales are never profit. With tax disabled (the default) tax is 0 and nothing changes.
 */
data class PeriodSummary(
    val range: PeriodRange,
    val grossSales: Long,
    val discounts: Long,
    val taxCollected: Long,
    val invoiceCount: Int,
    val averageInvoice: Long,
    val returnsTotal: Long,
    val returnsTax: Long,
    val netSales: Long,
    val cogs: Long,
    val grossProfit: Long,
    val expenses: Long,
    val inventoryLosses: Long,
    val estimatedNetProfit: Long,
)

/** [netSales] excludes tax, like [PeriodSummary.netSales]. */
data class DayPoint(val dayStart: Long, val grossSales: Long, val netSales: Long, val invoiceCount: Int)

data class PeriodComparison(val current: PeriodSummary, val previous: PeriodSummary)

/**
 * Net performance of one product in a period: sold minus returned, tax excluded.
 * revenue = line totals (after item discounts, before invoice-level discount and tax) - refunds without their tax part;
 * cost = cost snapshots of the sold lines - cost of resellable returned units; profit = revenue - cost.
 */
data class ProductPerformance(
    val productId: Long,
    val name: String,
    val categoryName: String?,
    val qtyMilli: Long,
    val revenue: Long,
    val cost: Long,
    val profit: Long,
)

/** [categoryName] null = products without a category. */
data class CategorySales(val categoryName: String?, val revenue: Long, val cost: Long, val profit: Long)

data class ExpenseByCategory(val category: ExpenseCategory, val total: Long)

data class HourStat(val hour: Int, val invoiceCount: Int, val sales: Long)

/** [dayOfWeek] is a java.util.Calendar constant (SUNDAY = 1 .. SATURDAY = 7). */
data class WeekdayStat(val dayOfWeek: Int, val invoiceCount: Int, val sales: Long, val occurrences: Int, val averageSales: Long)

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
            val net = (agg.gross - agg.tax) - (reports.returnsTotal(r.from, r.to) - returnsTaxIn(r))
            DayPoint(r.from, agg.gross, net, agg.invoiceCount)
        }
    }

    // ---------- product reports ----------

    /** Every product that was sold or returned in the period (unsorted). */
    suspend fun productPerformance(range: PeriodRange): List<ProductPerformance> = db.tx { performanceIn(range) }

    /** Best sellers by net quantity (sold minus returned). Products with no net sales are left out. */
    suspend fun topByQuantity(range: PeriodRange, limit: Int = 10): List<ProductPerformance> = db.tx {
        performanceIn(range).filter { it.qtyMilli > 0 }
            .sortedWith(compareByDescending<ProductPerformance> { it.qtyMilli }.thenBy { it.name })
            .take(limit)
    }

    /** Products that made the most profit. */
    suspend fun topByProfit(range: PeriodRange, limit: Int = 10): List<ProductPerformance> = db.tx {
        performanceIn(range).filter { it.qtyMilli > 0 }
            .sortedWith(compareByDescending<ProductPerformance> { it.profit }.thenBy { it.name })
            .take(limit)
    }

    /** Products that did sell, but the least. (Products that did not sell at all are in [nonMoving].) */
    suspend fun leastSold(range: PeriodRange, limit: Int = 10): List<ProductPerformance> = db.tx {
        performanceIn(range).filter { it.qtyMilli > 0 }
            .sortedWith(compareBy<ProductPerformance> { it.qtyMilli }.thenBy { it.name })
            .take(limit)
    }

    /** Active products that are in stock but had no sale at all in the period. */
    suspend fun nonMoving(range: PeriodRange): List<Product> = db.tx {
        val soldIds = reports.productSaleRows(range.from, range.to).map { it.productId }.toSet()
        db.products().getAllActive().filter { it.currentQtyMilli > 0 && it.id !in soldIds }
    }

    /** Net revenue, cost and profit per category, biggest revenue first. */
    suspend fun salesByCategory(range: PeriodRange): List<CategorySales> = db.tx {
        performanceIn(range).groupBy { it.categoryName }.map { (name, items) ->
            CategorySales(name, items.sumOf { it.revenue }, items.sumOf { it.cost }, items.sumOf { it.profit })
        }.sortedByDescending { it.revenue }
    }

    suspend fun expensesByCategory(range: PeriodRange): List<ExpenseByCategory> = db.tx {
        reports.expensesByCategory(range.from, range.to).map { ExpenseByCategory(ExpenseCategory.valueOf(it.category), it.total) }
    }

    // ---------- time analysis ----------

    /** 24 entries (hour 0..23) counting completed invoices by the hour they were completed (invoice totals, tax included). */
    suspend fun peakHours(range: PeriodRange): List<HourStat> = db.tx {
        val count = IntArray(24)
        val sales = LongArray(24)
        val cal = Calendar.getInstance(zone)
        for (row in reports.saleTimes(range.from, range.to)) {
            cal.timeInMillis = row.completedAt
            val h = cal.get(Calendar.HOUR_OF_DAY)
            count[h]++
            sales[h] += row.total
        }
        (0..23).map { HourStat(it, count[it], sales[it]) }
    }

    /**
     * Seven entries (Calendar.SUNDAY..SATURDAY): total sales and invoices per weekday, and the average per occurrence of
     * that weekday inside the range (counted up to now, so a month in progress is not diluted by future days).
     */
    suspend fun weekdayComparison(range: PeriodRange): List<WeekdayStat> = db.tx {
        val count = IntArray(8)
        val sales = LongArray(8)
        val cal = Calendar.getInstance(zone)
        for (row in reports.saleTimes(range.from, range.to)) {
            cal.timeInMillis = row.completedAt
            val d = cal.get(Calendar.DAY_OF_WEEK)
            count[d]++
            sales[d] += row.total
        }
        val occurrences = IntArray(8)
        val end = minOf(range.to, clock())
        val walker = Calendar.getInstance(zone).apply { timeInMillis = range.from }
        while (walker.timeInMillis <= end) {
            occurrences[walker.get(Calendar.DAY_OF_WEEK)]++
            walker.add(Calendar.DAY_OF_MONTH, 1)
        }
        (Calendar.SUNDAY..Calendar.SATURDAY).map { WeekdayStat(it, count[it], sales[it], occurrences[it], average(sales[it], occurrences[it])) }
    }

    // ---------- internals ----------
    private suspend fun summaryIn(range: PeriodRange): PeriodSummary {
        val agg = reports.salesAggregate(range.from, range.to)
        val returns = reports.returnsTotal(range.from, range.to)
        val returnsTax = returnsTaxIn(range)
        val returnedCost = valueOf(reports.resellableReturnRows(range.from, range.to))
        val losses = valueOf(reports.lossRows(range.from, range.to))
        val expenses = reports.expensesTotal(range.from, range.to)

        val netSales = (agg.gross - agg.tax) - (returns - returnsTax)
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
            returnsTax = returnsTax,
            netSales = netSales,
            cogs = cogs,
            grossProfit = grossProfit,
            expenses = expenses,
            inventoryLosses = losses,
            estimatedNetProfit = grossProfit - expenses - losses,
        )
    }

    /** Tax part of the refunds of the period; each return is split in proportion to its invoice's tax / total. */
    private suspend fun returnsTaxIn(range: PeriodRange): Long =
        reports.returnTaxRows(range.from, range.to).sumOf { taxPart(it.refund, it.invoiceTax, it.invoiceTotal) }

    private fun taxPart(refund: Long, invoiceTax: Long, invoiceTotal: Long): Long =
        if (invoiceTax == 0L || invoiceTotal <= 0L) 0L else Allocation.portion(invoiceTax, refund, invoiceTotal)

    private class Acc(val name: String, val categoryName: String?) {
        var qty = 0L
        var revenue = 0L
        var cost = 0L
    }

    private suspend fun performanceIn(range: PeriodRange): List<ProductPerformance> {
        val acc = LinkedHashMap<Long, Acc>()
        for (r in reports.productSaleRows(range.from, range.to)) {
            val a = acc.getOrPut(r.productId) { Acc(r.productName, r.categoryName) }
            a.qty += r.qtyMilli
            a.revenue += r.lineTotal
            a.cost += Money.costOf(r.unitCost, r.qtyMilli)
        }
        for (r in reports.productReturnRows(range.from, range.to)) {
            val a = acc.getOrPut(r.productId) { Acc(r.productName, r.categoryName) }
            a.qty -= r.qtyMilli
            a.revenue -= r.refundAmount - taxPart(r.refundAmount, r.invoiceTax, r.invoiceTotal)
            if (r.resellable) a.cost -= Money.costOf(r.unitCost, r.qtyMilli)
        }
        return acc.map { (id, a) -> ProductPerformance(id, a.name, a.categoryName, a.qtyMilli, a.revenue, a.cost, a.revenue - a.cost) }
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
