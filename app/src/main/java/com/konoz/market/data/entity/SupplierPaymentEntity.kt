package com.konoz.market.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cash that left (or, for a supplier return, came back into) the drawer because of a supplier invoice.
 * [amount] is signed: positive = we paid the supplier, negative = the supplier refunded us.
 * [shiftId] is the shift that was open at the time (null = no shift was open, so no drawer is affected).
 * This is not an expense: purchases enter the books as stock/COGS, so profit reports never read this table.
 */
@Entity(tableName = "supplier_payments")
data class SupplierPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseInvoiceId: Long,
    val supplierId: Long,
    val shiftId: Long?,
    val amount: Long,
    val userId: Long?,
    val createdAt: Long,
)
