package com.konoz.market.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.konoz.market.data.entity.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Insert suspend fun insert(c: Customer): Long
    @Update suspend fun update(c: Customer)

    @Query("DELETE FROM customers WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun getById(id: Long): Customer?
    @Query("SELECT * FROM customers WHERE phone = :phone") suspend fun getByPhone(phone: String): Customer?

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<Customer>>

    /** Records who a completed invoice was sent to. Returns 0 when the invoice is unknown or not completed. */
    @Query("UPDATE sales_invoices SET customerName = :name, customerPhone = :phone WHERE id = :invoiceId AND status = 'COMPLETED'")
    suspend fun setInvoiceCustomer(invoiceId: Long, name: String?, phone: String?): Int
}
