package com.konoz.market.domain

import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.StockBatch
import java.math.BigDecimal
import java.math.RoundingMode

data class AdjustmentResult(
    /** Cost value (piastres) of the stock that moved: for losses this is the write-off amount. */
    val costValue: Long,
    val duplicate: Boolean = false,
)

sealed class StockException(message: String) : Exception(message) {
    class InvalidQuantity : StockException("كمية غير صحيحة")
    class ReasonRequired : StockException("سبب التعديل مطلوب")
    class ProductNotFound(val productId: Long) : StockException("المنتج $productId غير موجود")
    class InsufficientStock(val productId: Long, val missingMilli: Long) : StockException("المخزون غير كافٍ للمنتج $productId")
    class SameProduct : StockException("لا يمكن التحويل إلى نفس المنتج")
}

/**
 * Manual stock operations: corrections, damaged/expired write-offs and unit conversion (carton -> pieces).
 * All go through the movement ledger, all take stock out FEFO (earliest expiry first), all run in one
 * transaction and are idempotent per uuid. Stock never goes below zero here.
 * [adjust] is also used by the stock count (type COUNT_ADJUSTMENT) when a count is approved.
 */
class StockAdjustmentService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val stock get() = db.stock()
    private val products get() = db.products()

    /**
     * Manual correction: delta > 0 adds stock (cost = current cost price), delta < 0 removes it. A reason is mandatory.
     * [type] is ADJUSTMENT, or COUNT_ADJUSTMENT when called by the stock count.
     */
    suspend fun adjust(
        uuid: String, productId: Long, deltaMilli: Long, reason: String, userId: Long?,
        type: MovementType = MovementType.ADJUSTMENT,
    ): AdjustmentResult = db.tx {
        require(type == MovementType.ADJUSTMENT || type == MovementType.COUNT_ADJUSTMENT) { "type must be ADJUSTMENT or COUNT_ADJUSTMENT" }
        if (deltaMilli == 0L) throw StockException.InvalidQuantity()
        if (reason.isBlank()) throw StockException.ReasonRequired()
        if (stock.movementExists("ADJ:$uuid:0") > 0) return@tx AdjustmentResult(0, duplicate = true)
        val product = loadProduct(productId, deltaMilli)
        val now = clock()
        val refType = if (type == MovementType.COUNT_ADJUSTMENT) "STOCK_COUNT" else "ADJUSTMENT"

        val value: Long
        if (deltaMilli > 0L) {
            val batchId = stock.insertBatch(StockBatch(productId = productId, qtyMilli = deltaMilli, unitCost = product.costPrice, receivedAt = now))
            stock.insertMovement(
                InventoryMovement(
                    productId = productId, batchId = batchId, type = type, qtyMilli = deltaMilli,
                    unitCost = product.costPrice, refType = refType, reason = reason.trim(), userId = userId,
                    idempotencyKey = "ADJ:$uuid:0", createdAt = now,
                )
            )
            products.adjustQty(productId, deltaMilli)
            value = Money.costOf(product.costPrice, deltaMilli)
        } else {
            value = deduct(product, -deltaMilli, type, "ADJ:$uuid", refType, reason.trim(), userId, now).cost
        }
        audit(userId, "STOCK_ADJUSTMENT", productId, "delta=$deltaMilli reason=${reason.trim()}", now)
        AdjustmentResult(value)
    }

    /** Removes damaged or expired goods from stock (FEFO). [type] must be DAMAGED or EXPIRED. */
    suspend fun writeOff(uuid: String, productId: Long, qtyMilli: Long, type: MovementType, reason: String?, userId: Long?): AdjustmentResult = db.tx {
        require(type == MovementType.DAMAGED || type == MovementType.EXPIRED) { "type must be DAMAGED or EXPIRED" }
        if (qtyMilli <= 0L) throw StockException.InvalidQuantity()
        if (stock.movementExists("WO:$uuid:0") > 0) return@tx AdjustmentResult(0, duplicate = true)
        val product = loadProduct(productId, qtyMilli)
        val now = clock()
        if (product.currentQtyMilli < qtyMilli) throw StockException.InsufficientStock(productId, qtyMilli - product.currentQtyMilli)
        val d = deduct(product, qtyMilli, type, "WO:$uuid", "WRITE_OFF", reason?.trim()?.ifEmpty { null }, userId, now)
        audit(userId, "STOCK_WRITE_OFF_${type.name}", productId, "qty=$qtyMilli cost=${d.cost}", now)
        AdjustmentResult(d.cost)
    }

    /**
     * Converts stock of one product into another (e.g. 2 cartons -> 24 pieces). The source is taken out FEFO and
     * the target receives a new batch whose unit cost is the consumed cost spread over the new quantity and which
     * keeps the earliest consumed expiry date.
     */
    suspend fun convertUnits(
        uuid: String,
        fromProductId: Long, fromQtyMilli: Long,
        toProductId: Long, toQtyMilli: Long,
        userId: Long?,
    ): AdjustmentResult = db.tx {
        if (fromProductId == toProductId) throw StockException.SameProduct()
        if (fromQtyMilli <= 0L || toQtyMilli <= 0L) throw StockException.InvalidQuantity()
        if (stock.movementExists("CONV:$uuid:in") > 0) return@tx AdjustmentResult(0, duplicate = true)
        val from = loadProduct(fromProductId, fromQtyMilli)
        val to = loadProduct(toProductId, toQtyMilli)
        if (from.currentQtyMilli < fromQtyMilli) throw StockException.InsufficientStock(fromProductId, fromQtyMilli - from.currentQtyMilli)
        val now = clock()

        val d = deduct(from, fromQtyMilli, MovementType.UNIT_CONVERSION, "CONV:$uuid:out", "UNIT_CONVERSION", null, userId, now)
        val unitCost = BigDecimal(d.cost).multiply(BigDecimal(1000)).divide(BigDecimal(toQtyMilli), 0, RoundingMode.HALF_UP).toLong()
        val batchId = stock.insertBatch(
            StockBatch(productId = toProductId, qtyMilli = toQtyMilli, unitCost = unitCost, expiryDate = d.firstExpiry, receivedAt = now)
        )
        stock.insertMovement(
            InventoryMovement(
                productId = toProductId, batchId = batchId, type = MovementType.UNIT_CONVERSION, qtyMilli = toQtyMilli,
                unitCost = unitCost, refType = "UNIT_CONVERSION", userId = userId, idempotencyKey = "CONV:$uuid:in", createdAt = now,
            )
        )
        products.adjustQty(toProductId, toQtyMilli)
        audit(userId, "UNIT_CONVERSION", fromProductId, "from=$fromQtyMilli to=$toQtyMilli target=$toProductId", now)
        AdjustmentResult(d.cost)
    }

    // ---------- internals ----------
    private class Deducted(val cost: Long, val firstExpiry: Long?)

    private suspend fun loadProduct(productId: Long, qtyMilli: Long): Product {
        val p = products.getById(productId) ?: throw StockException.ProductNotFound(productId)
        if (!p.unit.isFractional && qtyMilli % 1000L != 0L) throw StockException.InvalidQuantity()
        return p
    }

    /** Takes [qty] out FEFO, one movement per batch touched. The caller has checked the product holds enough. */
    private suspend fun deduct(
        product: Product, qty: Long, type: MovementType, keyBase: String, refType: String,
        reason: String?, userId: Long?, now: Long,
    ): Deducted {
        if ((type == MovementType.ADJUSTMENT || type == MovementType.COUNT_ADJUSTMENT) && product.currentQtyMilli < qty) {
            throw StockException.InsufficientStock(product.id, qty - product.currentQtyMilli)
        }
        var remaining = qty
        var cost = 0L
        var firstExpiry: Long? = null
        var index = 0
        for (b in stock.fefoBatches(product.id)) {
            if (remaining == 0L) break
            val take = minOf(b.qtyMilli, remaining)
            stock.insertMovement(
                InventoryMovement(
                    productId = product.id, batchId = b.id, type = type, qtyMilli = -take, unitCost = b.unitCost,
                    refType = refType, reason = reason, userId = userId, idempotencyKey = "$keyBase:$index", createdAt = now,
                )
            )
            stock.updateBatch(b.copy(qtyMilli = b.qtyMilli - take))
            cost += Money.costOf(b.unitCost, take)
            if (firstExpiry == null) firstExpiry = b.expiryDate
            remaining -= take
            index++
        }
        if (remaining > 0L) { // stock that was never placed in a batch
            stock.insertMovement(
                InventoryMovement(
                    productId = product.id, type = type, qtyMilli = -remaining, unitCost = product.costPrice,
                    refType = refType, reason = reason, userId = userId, idempotencyKey = "$keyBase:$index", createdAt = now,
                )
            )
            cost += Money.costOf(product.costPrice, remaining)
        }
        products.adjustQty(product.id, -qty)
        return Deducted(cost, firstExpiry)
    }

    private suspend fun audit(userId: Long?, action: String, productId: Long, details: String, now: Long) {
        db.audit().insert(AuditLog(userId = userId, action = action, entity = "product", entityId = productId, details = details, createdAt = now))
    }
}
