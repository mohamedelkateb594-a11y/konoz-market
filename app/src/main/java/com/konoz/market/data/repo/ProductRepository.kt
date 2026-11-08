package com.konoz.market.data.repo

import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.Barcode
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.StockBatch
import com.konoz.market.data.entity.UnitType
import kotlinx.coroutines.flow.Flow

data class BarcodeInput(val code: String, val unitsPerScan: Int = 1)

data class ProductInput(
    val name: String,
    /** 0 = create a new product, otherwise update this one. */
    val id: Long = 0,
    val internalCode: String? = null,
    val categoryId: Long? = null,
    val supplierId: Long? = null,
    val unit: UnitType = UnitType.PIECE,
    val costPrice: Long = 0,
    val salePrice: Long = 0,
    val lowStockThresholdMilli: Long = 0,
    val piecesPerCarton: Int? = null,
    val expiryDate: Long? = null,
    val imagePath: String? = null,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
    /** First barcode becomes the primary one. */
    val barcodes: List<BarcodeInput> = emptyList(),
    /** Only used when creating: becomes an OPENING movement + first stock batch. */
    val openingQtyMilli: Long = 0,
)

data class ProductDetails(val product: Product, val barcodes: List<Barcode>)

sealed class ProductException(message: String) : Exception(message) {
    class BlankName : ProductException("اسم المنتج مطلوب")
    class InvalidPrice : ProductException("سعر غير صحيح")
    class InvalidCartonSize : ProductException("عدد القطع داخل الكرتونة غير صحيح")
    class InvalidQuantity : ProductException("كمية غير صحيحة")
    class DuplicateBarcode(val code: String) : ProductException("الباركود $code مسجل لمنتج آخر")
    class DuplicateInternalCode(val code: String) : ProductException("الكود الداخلي $code مسجل لمنتج آخر")
    class NotFound : ProductException("المنتج غير موجود")
}

class ProductRepository(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val products get() = db.products()
    private val stock get() = db.stock()

    fun observeProducts(query: String): Flow<List<Product>> =
        if (query.isBlank()) products.observeAll() else products.searchManage(query.trim())

    fun observeCategories(): Flow<List<Category>> = db.categories().observeAll()

    suspend fun getDetails(id: Long): ProductDetails? {
        val p = products.getById(id) ?: return null
        return ProductDetails(p, db.barcodes().getForProduct(id))
    }

    /** Scanner lookup: the product and how many base units one scan represents (carton barcode = N). */
    suspend fun lookupBarcode(code: String): Pair<Product, Int>? {
        val b = db.barcodes().findByCode(code.trim()) ?: return null
        val p = products.getById(b.productId) ?: return null
        return p to b.unitsPerScan
    }

    suspend fun addCategory(name: String): Long = db.tx {
        val n = name.trim()
        if (n.isEmpty()) throw ProductException.BlankName()
        db.categories().findByName(n)?.id ?: db.categories().insert(Category(name = n))
    }

    /** Create or update a product, its barcodes and (on create) its opening stock, atomically. */
    suspend fun save(input: ProductInput, userId: Long?): Long = db.tx {
        val name = input.name.trim()
        if (name.isEmpty()) throw ProductException.BlankName()
        if (input.costPrice < 0 || input.salePrice < 0) throw ProductException.InvalidPrice()
        if (input.piecesPerCarton != null && input.piecesPerCarton < 1) throw ProductException.InvalidCartonSize()
        if (input.lowStockThresholdMilli < 0 || input.openingQtyMilli < 0) throw ProductException.InvalidQuantity()
        if (!input.unit.isFractional && input.openingQtyMilli % 1000L != 0L) throw ProductException.InvalidQuantity()

        val code = input.internalCode?.trim()?.takeIf { it.isNotEmpty() }
        if (code != null) {
            val other = products.findByInternalCode(code)
            if (other != null && other.id != input.id) throw ProductException.DuplicateInternalCode(code)
        }

        val barcodes = input.barcodes
            .map { it.copy(code = it.code.trim()) }
            .filter { it.code.isNotEmpty() }
            .distinctBy { it.code }
        for (b in barcodes) {
            if (b.unitsPerScan < 1) throw ProductException.InvalidQuantity()
            val existing = db.barcodes().findByCode(b.code)
            if (existing != null && existing.productId != input.id) throw ProductException.DuplicateBarcode(b.code)
        }

        val now = clock()
        if (input.id == 0L) {
            val id = products.insert(
                Product(
                    name = name, internalCode = code, categoryId = input.categoryId, supplierId = input.supplierId,
                    unit = input.unit, costPrice = input.costPrice, salePrice = input.salePrice,
                    lowStockThresholdMilli = input.lowStockThresholdMilli, piecesPerCarton = input.piecesPerCarton,
                    imagePath = input.imagePath, expiryDate = input.expiryDate, isFavorite = input.isFavorite,
                    isActive = input.isActive, createdAt = now,
                )
            )
            barcodes.forEachIndexed { i, b ->
                db.barcodes().insert(Barcode(productId = id, code = b.code, isPrimary = i == 0, unitsPerScan = b.unitsPerScan))
            }
            if (input.openingQtyMilli > 0L) {
                val batchId = stock.insertBatch(
                    StockBatch(productId = id, qtyMilli = input.openingQtyMilli, unitCost = input.costPrice,
                        expiryDate = input.expiryDate, receivedAt = now)
                )
                stock.insertMovement(
                    InventoryMovement(
                        productId = id, batchId = batchId, type = MovementType.OPENING, qtyMilli = input.openingQtyMilli,
                        unitCost = input.costPrice, refType = "PRODUCT", refId = id, userId = userId,
                        idempotencyKey = "OPEN:$id", createdAt = now,
                    )
                )
                products.adjustQty(id, input.openingQtyMilli)
            }
            audit(userId, "PRODUCT_CREATED", id, name)
            id
        } else {
            val existing = products.getById(input.id) ?: throw ProductException.NotFound()
            products.update(
                existing.copy(
                    name = name, internalCode = code, categoryId = input.categoryId, supplierId = input.supplierId,
                    unit = input.unit, costPrice = input.costPrice, salePrice = input.salePrice,
                    lowStockThresholdMilli = input.lowStockThresholdMilli, piecesPerCarton = input.piecesPerCarton,
                    imagePath = input.imagePath, expiryDate = input.expiryDate, isFavorite = input.isFavorite,
                    isActive = input.isActive,
                    // currentQtyMilli and createdAt are intentionally preserved: stock changes only via movements.
                )
            )
            val current = db.barcodes().getForProduct(existing.id)
            val wanted = barcodes.map { it.code }.toSet()
            current.filter { it.code !in wanted }.forEach { db.barcodes().delete(it.id) }
            barcodes.forEachIndexed { i, b ->
                val cur = current.firstOrNull { it.code == b.code }
                if (cur == null) {
                    db.barcodes().insert(Barcode(productId = existing.id, code = b.code, isPrimary = i == 0, unitsPerScan = b.unitsPerScan))
                } else if (cur.unitsPerScan != b.unitsPerScan || cur.isPrimary != (i == 0)) {
                    db.barcodes().update(cur.copy(unitsPerScan = b.unitsPerScan, isPrimary = i == 0))
                }
            }
            if (existing.salePrice != input.salePrice) {
                audit(userId, "PRICE_CHANGED", existing.id, "${existing.salePrice} -> ${input.salePrice}")
            }
            audit(userId, "PRODUCT_UPDATED", existing.id, name)
            existing.id
        }
    }

    suspend fun setActive(id: Long, active: Boolean, userId: Long?) = db.tx {
        products.setActive(id, active)
        audit(userId, if (active) "PRODUCT_ACTIVATED" else "PRODUCT_DEACTIVATED", id, null)
    }

    suspend fun toggleFavorite(id: Long) {
        val p = products.getById(id) ?: return
        products.update(p.copy(isFavorite = !p.isFavorite))
    }

    /**
     * Bulk price update: raises (or lowers, if negative) the sale price of active products by a percentage
     * given in basis points (10% = 1000). categoryId = null means all active products. Returns how many were matched.
     */
    suspend fun bulkAdjustSalePrice(categoryId: Long?, basisPoints: Int, userId: Long?): Int = db.tx {
        val matched = products.getAllActive().filter { categoryId == null || it.categoryId == categoryId }
        for (p in matched) {
            val newPrice = maxOf(0L, p.salePrice + Money.percentOf(p.salePrice, basisPoints))
            if (newPrice != p.salePrice) products.setSalePrice(p.id, newPrice)
        }
        audit(userId, "BULK_PRICE_CHANGE", null, "category=$categoryId bp=$basisPoints count=${matched.size}")
        matched.size
    }

    private suspend fun audit(userId: Long?, action: String, entityId: Long?, details: String?) {
        db.audit().insert(
            AuditLog(userId = userId, action = action, entity = "product", entityId = entityId, details = details, createdAt = clock())
        )
    }
}
