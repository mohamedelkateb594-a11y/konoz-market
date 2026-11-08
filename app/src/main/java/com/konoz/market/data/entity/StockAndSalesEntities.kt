package com.konoz.market.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Remaining quantity of one received lot (batch) of a product. Sold FEFO (earliest expiry first). */
@Entity(
    tableName = "stock_batches",
    foreignKeys = [ForeignKey(Product::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("productId"), Index("expiryDate")],
)
data class StockBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val batchNo: String? = null,
    val qtyMilli: Long,
    val unitCost: Long,
    val expiryDate: Long? = null,
    val receivedAt: Long,
)

/**
 * Append-only ledger. qtyMilli is signed (+ in, - out).
 * idempotencyKey is UNIQUE, so a retried posting of the same document is silently ignored.
 */
@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(Product::class, ["id"], ["productId"]),
        ForeignKey(StockBatch::class, ["id"], ["batchId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("productId"), Index("batchId"), Index("createdAt"),
        Index("refType", "refId"), Index("idempotencyKey", unique = true),
    ],
)
data class InventoryMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val batchId: Long? = null,
    val type: MovementType,
    val qtyMilli: Long,
    val unitCost: Long = 0,
    val refType: String? = null,
    val refId: Long? = null,
    val reason: String? = null,
    val userId: Long? = null,
    val idempotencyKey: String,
    val createdAt: Long,
)

@Entity(
    tableName = "sales_invoices",
    foreignKeys = [
        ForeignKey(User::class, ["id"], ["userId"]),
        ForeignKey(Shift::class, ["id"], ["shiftId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("uuid", unique = true), Index("number", unique = true),
        Index("userId"), Index("shiftId"), Index("status"), Index("completedAt"),
    ],
)
data class SalesInvoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id used to detect duplicates when restoring a backup. */
    val uuid: String,
    /** Sequential number, assigned only on completion (held invoices keep null, no gaps). */
    val number: Long? = null,
    val userId: Long,
    val shiftId: Long? = null,
    val status: InvoiceStatus = InvoiceStatus.HELD,
    val subtotal: Long = 0,
    val itemDiscountTotal: Long = 0,
    val invoiceDiscount: Long = 0,
    val taxBasisPoints: Int = 0,
    val taxAmount: Long = 0,
    val total: Long = 0,
    /** Snapshot of COGS at sale time. Never recomputed from the current product cost. */
    val totalCost: Long = 0,
    val paidTotal: Long = 0,
    val changeDue: Long = 0,
    val notes: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
    val stockPosted: Boolean = false,
)

@Entity(
    tableName = "sales_items",
    foreignKeys = [
        ForeignKey(SalesInvoice::class, ["id"], ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Product::class, ["id"], ["productId"]),
    ],
    indices = [Index("invoiceId"), Index("productId")],
)
data class SalesItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val productName: String,
    val barcode: String? = null,
    val qtyMilli: Long,
    val unitPrice: Long,
    val unitCostAtSale: Long,
    val discount: Long = 0,
    val lineTotal: Long,
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(SalesInvoice::class, ["id"], ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Shift::class, ["id"], ["shiftId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("invoiceId"), Index("shiftId")],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val shiftId: Long? = null,
    val method: PaymentMethod,
    val amount: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "sales_returns",
    foreignKeys = [
        ForeignKey(SalesInvoice::class, ["id"], ["salesInvoiceId"]),
        ForeignKey(User::class, ["id"], ["userId"]),
    ],
    indices = [Index("salesInvoiceId"), Index("userId"), Index("createdAt")],
)
data class SalesReturn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val salesInvoiceId: Long,
    val userId: Long,
    val reason: String? = null,
    val refundAmount: Long,
    val refundMethod: PaymentMethod = PaymentMethod.CASH,
    val shiftId: Long? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "return_items",
    foreignKeys = [
        ForeignKey(SalesReturn::class, ["id"], ["returnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SalesItem::class, ["id"], ["salesItemId"]),
        ForeignKey(Product::class, ["id"], ["productId"]),
    ],
    indices = [Index("returnId"), Index("salesItemId"), Index("productId")],
)
data class ReturnItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val salesItemId: Long,
    val productId: Long,
    val qtyMilli: Long,
    val refundAmount: Long,
    val condition: ReturnCondition = ReturnCondition.RESELLABLE,
)
