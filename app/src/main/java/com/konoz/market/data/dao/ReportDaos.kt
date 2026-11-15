package com.konoz.market.data.dao

import androidx.room.Dao
import androidx.room.Query

/** Sums over completed sales in a period. Money in piastres. */
data class SalesAggregate(
    /** Sum of invoice totals: what customers paid, tax included. */
    val gross: Long,
    val invoiceCount: Int,
    /** Sum of the COGS snapshots stored on the invoices (never recomputed from current product cost). */
    val soldCost: Long,
    val discounts: Long,
    /** Tax collected on those invoices (a liability, not revenue). */
    val tax: Long,
)

/** A quantity (milli-units) with the unit cost (piastres) it should be valued at. */
data class QtyCostRow(val qtyMilli: Long, val unitCost: Long)

/** One sold invoice line with the product's category name (null = uncategorized). */
data class ProductSaleRow(
    val productId: Long,
    val productName: String,
    val categoryName: String?,
    val qtyMilli: Long,
    val lineTotal: Long,
    val unitCost: Long,
)

/**
 * One returned line (any condition) with the cost snapshot of the original sale.
 * [invoiceTax] / [invoiceTotal] belong to the original invoice and let the refund be split into revenue and tax.
 */
data class ProductReturnRow(
    val productId: Long,
    val productName: String,
    val categoryName: String?,
    val qtyMilli: Long,
    val refundAmount: Long,
    val unitCost: Long,
    val resellable: Boolean,
    val invoiceTax: Long,
    val invoiceTotal: Long,
)

/** One customer return with the tax and total of the invoice it belongs to. */
data class ReturnTaxRow(val refund: Long, val invoiceTax: Long, val invoiceTotal: Long)

data class ExpenseCategoryTotal(val category: String, val total: Long)

data class SaleTimeRow(val completedAt: Long, val total: Long)

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

    @Query(
        """SELECT r.refundAmount AS refund, inv.taxAmount AS invoiceTax, inv.total AS invoiceTotal
           FROM sales_returns r INNER JOIN sales_invoices inv ON inv.id = r.salesInvoiceId
           WHERE r.createdAt BETWEEN :from AND :to"""
    )
    suspend fun returnTaxRows(from: Long, to: Long): List<ReturnTaxRow>

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

    // ---------- breakdowns ----------

    @Query(
        """SELECT si.productId AS productId, si.productName AS productName, c.name AS categoryName,
                  si.qtyMilli AS qtyMilli, si.lineTotal AS lineTotal, si.unitCostAtSale AS unitCost
           FROM sales_items si
           INNER JOIN sales_invoices inv ON inv.id = si.invoiceId
           INNER JOIN products p ON p.id = si.productId
           LEFT JOIN categories c ON c.id = p.categoryId
           WHERE inv.status = 'COMPLETED' AND inv.completedAt BETWEEN :from AND :to"""
    )
    suspend fun productSaleRows(from: Long, to: Long): List<ProductSaleRow>

    @Query(
        """SELECT ri.productId AS productId, p.name AS productName, c.name AS categoryName,
                  ri.qtyMilli AS qtyMilli, ri.refundAmount AS refundAmount, si.unitCostAtSale AS unitCost,
                  (ri.condition = 'RESELLABLE') AS resellable,
                  inv.taxAmount AS invoiceTax, inv.total AS invoiceTotal
           FROM return_items ri
           INNER JOIN sales_returns r ON r.id = ri.returnId
           INNER JOIN sales_invoices inv ON inv.id = r.salesInvoiceId
           INNER JOIN sales_items si ON si.id = ri.salesItemId
           INNER JOIN products p ON p.id = ri.productId
           LEFT JOIN categories c ON c.id = p.categoryId
           WHERE r.createdAt BETWEEN :from AND :to"""
    )
    suspend fun productReturnRows(from: Long, to: Long): List<ProductReturnRow>

    @Query(
        """SELECT category AS category, SUM(amount) AS total FROM expenses
           WHERE createdAt BETWEEN :from AND :to GROUP BY category ORDER BY total DESC"""
    )
    suspend fun expensesByCategory(from: Long, to: Long): List<ExpenseCategoryTotal>

    @Query(
        """SELECT completedAt AS completedAt, total AS total FROM sales_invoices
           WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to"""
    )
    suspend fun saleTimes(from: Long, to: Long): List<SaleTimeRow>
}
