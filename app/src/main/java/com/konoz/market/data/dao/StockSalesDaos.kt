package com.konoz.market.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.Payment
import com.konoz.market.data.entity.ReturnItem
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesItem
import com.konoz.market.data.entity.SalesReturn
import com.konoz.market.data.entity.StockBatch
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Insert suspend fun insertBatch(b: StockBatch): Long
    @Update suspend fun updateBatch(b: StockBatch)

    /** Returns -1 when the idempotencyKey already exists (movement was already posted). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovement(m: InventoryMovement): Long

    @Query("SELECT COUNT(*) FROM inventory_movements WHERE idempotencyKey = :key")
    suspend fun movementExists(key: String): Int

    /** FEFO: earliest expiry first; batches without expiry last; ties by oldest received. */
    @Query(
        """SELECT * FROM stock_batches WHERE productId = :productId AND qtyMilli > 0
           ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate ASC, receivedAt ASC, id ASC"""
    )
    suspend fun fefoBatches(productId: Long): List<StockBatch>

    @Query("SELECT * FROM stock_batches WHERE productId = :productId ORDER BY receivedAt DESC")
    fun observeBatches(productId: Long): Flow<List<StockBatch>>

    @Query("SELECT COALESCE(SUM(qtyMilli), 0) FROM stock_batches WHERE productId = :productId")
    suspend fun batchTotal(productId: Long): Long

    @Query("SELECT COALESCE(SUM(qtyMilli), 0) FROM inventory_movements WHERE productId = :productId")
    suspend fun movementTotal(productId: Long): Long

    @Query("SELECT * FROM inventory_movements WHERE productId = :productId ORDER BY createdAt DESC, id DESC")
    fun observeMovements(productId: Long): Flow<List<InventoryMovement>>

    @Query(
        """SELECT * FROM stock_batches WHERE qtyMilli > 0 AND expiryDate IS NOT NULL
           AND expiryDate > :now AND expiryDate <= :until ORDER BY expiryDate"""
    )
    fun observeExpiringSoon(now: Long, until: Long): Flow<List<StockBatch>>

    @Query(
        """SELECT * FROM stock_batches WHERE qtyMilli > 0 AND expiryDate IS NOT NULL
           AND expiryDate <= :now ORDER BY expiryDate"""
    )
    fun observeExpired(now: Long): Flow<List<StockBatch>>
}

@Dao
interface SalesDao {
    @Insert suspend fun insertInvoice(i: SalesInvoice): Long
    @Update suspend fun updateInvoice(i: SalesInvoice)
    @Insert suspend fun insertItems(items: List<SalesItem>): List<Long>
    @Insert suspend fun insertPayments(p: List<Payment>)

    @Query("DELETE FROM sales_items WHERE invoiceId = :invoiceId") suspend fun deleteItems(invoiceId: Long)
    @Query("DELETE FROM payments WHERE invoiceId = :invoiceId") suspend fun deletePayments(invoiceId: Long)
    @Query("DELETE FROM sales_invoices WHERE id = :id AND status = 'HELD'") suspend fun deleteHeld(id: Long)

    @Query("SELECT * FROM sales_invoices WHERE id = :id") suspend fun getInvoice(id: Long): SalesInvoice?
    @Query("SELECT * FROM sales_invoices WHERE uuid = :uuid") suspend fun getByUuid(uuid: String): SalesInvoice?
    @Query("SELECT * FROM sales_invoices WHERE number = :number") suspend fun getByNumber(number: Long): SalesInvoice?
    @Query("SELECT * FROM sales_items WHERE invoiceId = :invoiceId ORDER BY id") suspend fun getItems(invoiceId: Long): List<SalesItem>
    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY id") suspend fun getPayments(invoiceId: Long): List<Payment>

    @Query("SELECT COALESCE(MAX(number), 0) + 1 FROM sales_invoices") suspend fun nextNumber(): Long

    @Query("SELECT * FROM sales_invoices WHERE status = 'HELD' ORDER BY createdAt DESC")
    fun observeHeld(): Flow<List<SalesInvoice>>

    @Query("SELECT * FROM sales_invoices WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SalesInvoice>>

    @Query("SELECT * FROM sales_invoices WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to ORDER BY completedAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<SalesInvoice>>

    @Query("SELECT COALESCE(SUM(total), 0) FROM sales_invoices WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to")
    fun observeSalesTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM sales_invoices WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to")
    fun observeInvoiceCount(from: Long, to: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalCost), 0) FROM sales_invoices WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to")
    fun observeCostTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(itemDiscountTotal + invoiceDiscount), 0) FROM sales_invoices WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to")
    fun observeDiscountTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE shiftId = :shiftId AND method = :method")
    suspend fun paymentsTotalForShift(shiftId: Long, method: String): Long

    // Returns
    @Insert suspend fun insertReturn(r: SalesReturn): Long
    @Insert suspend fun insertReturnItems(items: List<ReturnItem>)
    @Query("SELECT * FROM sales_returns WHERE uuid = :uuid") suspend fun getReturnByUuid(uuid: String): SalesReturn?
    @Query("SELECT * FROM return_items WHERE returnId = :returnId ORDER BY id") suspend fun getReturnItems(returnId: Long): List<ReturnItem>
    @Query("SELECT * FROM sales_returns WHERE salesInvoiceId = :invoiceId ORDER BY createdAt")
    suspend fun returnsForInvoice(invoiceId: Long): List<SalesReturn>
    @Query("SELECT COALESCE(SUM(qtyMilli), 0) FROM return_items WHERE salesItemId = :salesItemId")
    suspend fun returnedQty(salesItemId: Long): Long
    @Query("SELECT * FROM sales_returns ORDER BY createdAt DESC") fun observeReturns(): Flow<List<SalesReturn>>
    @Query("SELECT COALESCE(SUM(refundAmount), 0) FROM sales_returns WHERE createdAt BETWEEN :from AND :to")
    fun observeReturnsTotal(from: Long, to: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(refundAmount), 0) FROM sales_returns WHERE shiftId = :shiftId AND refundMethod = 'CASH'")
    suspend fun cashRefundsForShift(shiftId: Long): Long
}
