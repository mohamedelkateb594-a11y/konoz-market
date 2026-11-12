package com.konoz.market.data.dao

import androidx.room.Dao
import androidx.room.Query

/** Sums over completed sales in a period. Money in piastres. */
data class SalesAggregate(
    val gross: Long,
    val invoiceCount: Int,
    /** Sum of the COGS snapshots stored on the invoices (never recomputed from current product cost). */
    val soldCost: Long,
    val discounts: Long,
    val tax: Long,
)

/** A quantity (milli-units) with the unit cost (piastres) it should be valued at. */
data class QtyCostRow(val qtyMilli: Long, val unitCost: Long)

/** Read-only queries behind the reports and the dashboard. */
@Dao
interface ReportDao {
    @Query(
        """SELECT COALESCE(SUM(total), 0) AS gross,
                  COUNT(*) AS invoiceCount,
                  COALESCE(SUM(totalCost), 0) AS soldCost,
                  COALESCE(SUM(itemDiscountTotal + invoiceDiscount), 0) AS discounts,
                  COALESCE(SUM(taxAmount), 0) AS tax
           FROM sales_invoices WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to"""
    )
    suspend fun salesAggregate(from: Long, to: Long): SalesAggregate

    /** Money refunded to customers in the period (by the date of the return, not of the invoice). */
    @Query("SELECT COALESCE(SUM(refundAmount), 0) FROM sales_returns WHERE createdAt BETWEEN :from AND :to")
    suspend fun returnsTotal(from: Long, to: Long): Long

    /** Returned units that went back to stock, valued at the cost snapshot of the original sale. */
    @Query(
        """SELECT ri.qtyMilli AS qtyMilli, si.unitCostAtSale AS unitCost
           FROM return_items ri
           INNER JOIN sales_returns r ON r.id = ri.returnId
           INNER JOIN sales_items si ON si.id = ri.salesItemId
           WHERE ri.condition = 'RESELLABLE' AND r.createdAt BETWEEN :from AND :to"""
    )
    suspend fun resellableReturnRows(from: Long, to: Long): List<QtyCostRow>

    /**
     * Stock written off outside of sales: damaged, expired, negative adjustments and count shortages.
     * Movements created by customer returns are excluded (a damaged return's cost is already inside COGS).
     */
    @Query(
        """SELECT -qtyMilli AS qtyMilli, unitCost AS unitCost FROM inventory_movements
           WHERE type IN ('DAMAGED', 'EXPIRED', 'ADJUSTMENT', 'COUNT_ADJUSTMENT') AND qtyMilli < 0
           AND (refType IS NULL OR refType <> 'SALES_RETURN') AND createdAt BETWEEN :from AND :to"""
    )
    suspend fun lossRows(from: Long, to: Long): List<QtyCostRow>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE createdAt BETWEEN :from AND :to")
    suspend fun expensesTotal(from: Long, to: Long): Long
}
