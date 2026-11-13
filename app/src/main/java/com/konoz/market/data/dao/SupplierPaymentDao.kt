package com.konoz.market.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.konoz.market.data.entity.SupplierPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierPaymentDao {
    @Insert suspend fun insert(p: SupplierPayment): Long

    /** Net cash paid to suppliers during a shift (refunds count negative). */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM supplier_payments WHERE shiftId = :shiftId")
    suspend fun netForShift(shiftId: Long): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM supplier_payments WHERE shiftId = :shiftId")
    fun observeNetForShift(shiftId: Long): Flow<Long>

    @Query("SELECT * FROM supplier_payments WHERE purchaseInvoiceId = :invoiceId ORDER BY createdAt, id")
    suspend fun forInvoice(invoiceId: Long): List<SupplierPayment>
}
