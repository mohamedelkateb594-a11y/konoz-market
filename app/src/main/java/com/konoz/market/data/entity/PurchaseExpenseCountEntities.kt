package com.konoz.market.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_invoices",
    foreignKeys = [
        ForeignKey(Supplier::class, ["id"], ["supplierId"]),
        ForeignKey(User::class, ["id"], ["userId"]),
    ],
    indices = [
        Index("supplierId"), Index("userId"), Index("status"),
        Index("uuid", unique = true), Index("number", unique = true),
    ],
)
data class PurchaseInvoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val number: Long? = null,
    val supplierId: Long,
    val userId: Long,
    val status: PurchaseStatus = PurchaseStatus.DRAFT,
    val subtotal: Long = 0,
    val discount: Long = 0,
    val total: Long = 0,
    val paid: Long = 0,
    val remaining: Long = 0,
    val isReturn: Boolean = false,
    val notes: String? = null,
    val createdAt: Long,
    val approvedAt: Long? = null,
    val stockPosted: Boolean = false,
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(PurchaseInvoice::class, ["id"], ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Product::class, ["id"], ["productId"]),
    ],
    indices = [Index("invoiceId"), Index("productId")],
)
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val qtyMilli: Long,
    val unitCost: Long,
    val discount: Long = 0,
    val lineTotal: Long,
    val expiryDate: Long? = null,
    val batchNo: String? = null,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(User::class, ["id"], ["userId"]),
        ForeignKey(Shift::class, ["id"], ["shiftId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("createdAt"), Index("category"), Index("userId"), Index("shiftId"), Index("uuid", unique = true)],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Idempotency key: retrying the same "add expense" request never creates a second row. */
    val uuid: String,
    val amount: Long,
    val category: ExpenseCategory,
    val description: String,
    val notes: String? = null,
    val receiptPath: String? = null,
    val paidInCash: Boolean = true,
    val userId: Long,
    val shiftId: Long? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "stock_counts",
    foreignKeys = [
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(User::class, ["id"], ["startedByUserId"]),
        ForeignKey(User::class, ["id"], ["approvedByUserId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("startedByUserId"), Index("approvedByUserId"), Index("status")],
)
data class StockCount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: CountStatus = CountStatus.DRAFT,
    val scope: CountScope = CountScope.ALL,
    val categoryId: Long? = null,
    val startedByUserId: Long,
    val approvedByUserId: Long? = null,
    val notes: String? = null,
    val startedAt: Long,
    val approvedAt: Long? = null,
)

@Entity(
    tableName = "stock_count_items",
    foreignKeys = [
        ForeignKey(StockCount::class, ["id"], ["countId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Product::class, ["id"], ["productId"]),
    ],
    indices = [Index("countId"), Index("productId"), Index("countId", "productId", unique = true)],
)
data class StockCountItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countId: Long,
    val productId: Long,
    val systemQtyMilli: Long,
    /** null = not counted yet. */
    val countedQtyMilli: Long? = null,
    val unitCostAtCount: Long,
)
