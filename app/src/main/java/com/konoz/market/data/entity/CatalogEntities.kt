package com.konoz.market.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// All money fields: Long piastres. All quantity fields: Long milli-units. See core/Money.kt.

@Entity(tableName = "categories", indices = [Index("name", unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Supplier::class, ["id"], ["supplierId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("categoryId"), Index("supplierId"), Index("name"),
        Index("internalCode", unique = true), Index("isFavorite"),
    ],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val internalCode: String? = null,
    val categoryId: Long? = null,
    val supplierId: Long? = null,
    val unit: UnitType = UnitType.PIECE,
    val costPrice: Long = 0,
    val salePrice: Long = 0,
    /** Cache of SUM(movements). Only changed inside the same transaction that writes a movement. */
    val currentQtyMilli: Long = 0,
    val lowStockThresholdMilli: Long = 0,
    val piecesPerCarton: Int? = null,
    val imagePath: String? = null,
    val expiryDate: Long? = null,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
    val warehouseId: Long = 1,
    val createdAt: Long,
)

/** A product can have many barcodes / QR codes. unitsPerScan = 12 for a carton barcode of 12 pieces. */
@Entity(
    tableName = "barcodes",
    foreignKeys = [ForeignKey(Product::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("productId"), Index("code", unique = true)],
)
data class Barcode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val code: String,
    val isPrimary: Boolean = false,
    val unitsPerScan: Int = 1,
)

@Entity(tableName = "users", indices = [Index("username", unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val username: String,
    val pinHash: String,
    val salt: String,
    val role: UserRole,
    val isActive: Boolean = true,
    val createdAt: Long,
)

@Entity(
    tableName = "shifts",
    foreignKeys = [ForeignKey(User::class, ["id"], ["userId"])],
    indices = [Index("userId"), Index("status")],
)
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingCash: Long,
    val expectedCash: Long? = null,
    val actualCash: Long? = null,
    val difference: Long? = null,
    val status: ShiftStatus = ShiftStatus.OPEN,
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "audit_logs", indices = [Index("createdAt"), Index("entity")])
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long? = null,
    val action: String,
    val entity: String,
    val entityId: Long? = null,
    val details: String? = null,
    val createdAt: Long,
)
