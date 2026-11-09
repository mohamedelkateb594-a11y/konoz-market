package com.konoz.market.domain

import com.konoz.market.core.Money
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.UnitType

data class CartItem(
    val productId: Long,
    val name: String,
    val unit: UnitType,
    /** Catalog price at the time the line was added (used to show overrides). */
    val listPrice: Long,
    val unitPrice: Long,
    val qtyMilli: Long,
    val discount: Long = 0,
    val barcode: String? = null,
) {
    val gross: Long get() = Money.lineTotal(unitPrice, qtyMilli)
    val net: Long get() = gross - discount
}

/**
 * Immutable shopping cart. Pure logic, no database. Every operation returns a new, normalized cart:
 * line discounts never exceed the line total and the invoice discount never exceeds the remainder.
 */
data class Cart(
    val uuid: String,
    val items: List<CartItem> = emptyList(),
    val invoiceDiscount: Long = 0,
    val taxBasisPoints: Int = 0,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    /** Adds [units] base units (a carton barcode adds N). Scanning the same product again increases the quantity. */
    fun add(product: Product, units: Int = 1, barcode: String? = null): Cart {
        val addMilli = units * 1000L
        val idx = items.indexOfFirst { it.productId == product.id }
        val updated = if (idx >= 0) {
            items.toMutableList().also { it[idx] = it[idx].copy(qtyMilli = it[idx].qtyMilli + addMilli) }
        } else {
            items + CartItem(product.id, product.name, product.unit, product.salePrice, product.salePrice, addMilli, 0, barcode)
        }
        return copy(items = updated).normalized()
    }

    fun setQty(productId: Long, qtyMilli: Long): Cart =
        if (qtyMilli <= 0L) remove(productId) else mapItem(productId) { it.copy(qtyMilli = qtyMilli) }

    fun remove(productId: Long): Cart = copy(items = items.filterNot { it.productId == productId }).normalized()

    fun setUnitPrice(productId: Long, price: Long): Cart =
        mapItem(productId) { it.copy(unitPrice = price.coerceAtLeast(0L)) }

    fun setLineDiscount(productId: Long, discount: Long): Cart =
        mapItem(productId) { it.copy(discount = discount.coerceAtLeast(0L)) }

    fun setInvoiceDiscount(discount: Long): Cart = copy(invoiceDiscount = discount.coerceAtLeast(0L)).normalized()

    fun lines(): List<CartLine> =
        items.map { CartLine(it.productId, it.qtyMilli, it.unitPrice, it.discount, it.barcode) }

    fun totals(): SaleTotals? = if (items.isEmpty()) null else SaleCalculator.totals(lines(), invoiceDiscount, taxBasisPoints)

    fun toDraft(userId: Long, shiftId: Long?): SaleDraft =
        SaleDraft(uuid, userId, shiftId, lines(), invoiceDiscount, taxBasisPoints)

    private fun mapItem(productId: Long, f: (CartItem) -> CartItem): Cart =
        copy(items = items.map { if (it.productId == productId) f(it) else it }).normalized()

    private fun normalized(): Cart {
        val clamped = items.map { it.copy(discount = it.discount.coerceIn(0L, it.gross)) }
        val afterItems = clamped.sumOf { it.gross - it.discount }
        val invoiceDisc = if (clamped.isEmpty()) 0L else invoiceDiscount.coerceIn(0L, afterItems)
        return copy(items = clamped, invoiceDiscount = invoiceDisc)
    }

    companion object {
        /** Rebuilds a cart from a held invoice. Lines whose product no longer exists are dropped. */
        fun restore(draft: SaleDraft, taxBasisPoints: Int, productsById: Map<Long, Product>): Cart {
            val items = draft.lines.mapNotNull { line ->
                val p = productsById[line.productId] ?: return@mapNotNull null
                CartItem(p.id, p.name, p.unit, p.salePrice, line.unitPrice, line.qtyMilli, line.discount, line.barcode)
            }
            return Cart(draft.uuid, items, draft.invoiceDiscount, taxBasisPoints).normalized()
        }
    }
}
