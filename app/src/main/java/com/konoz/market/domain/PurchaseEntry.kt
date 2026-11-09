package com.konoz.market.domain

import com.konoz.market.core.ExpiryDates
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.UnitType

/** A purchase/return line as typed on screen: every number is still text and is parsed only when needed. */
data class PurchaseEntryLine(
    val productId: Long,
    val name: String,
    val unit: UnitType,
    val qty: String = "1",
    val cost: String = "",
    val discount: String = "",
    /** UTC midnight millis from the date picker (purchases only). */
    val expiryUtcMillis: Long? = null,
    val batchNo: String = "",
)

data class ParsedEntry(val lines: List<PurchaseLine>, val invoiceDiscount: Long, val paid: Long)

/** Pure state of the purchase screen. Nothing here touches the database. */
data class PurchaseEntry(
    val lines: List<PurchaseEntryLine> = emptyList(),
    val invoiceDiscount: String = "",
    val paid: String = "",
    val notes: String = "",
) {
    /** Adds [units] base units; adding the same product again increases its quantity. Cost defaults to the current cost price. */
    fun addProduct(product: Product, units: Int = 1): PurchaseEntry {
        val i = lines.indexOfFirst { it.productId == product.id }
        if (i >= 0) {
            val line = lines[i]
            val qty = (Quantities.parseMilli(line.qty) ?: 0L) + units * 1000L
            return copy(lines = lines.toMutableList().also { it[i] = line.copy(qty = Quantities.format(qty)) })
        }
        return copy(
            lines = lines + PurchaseEntryLine(
                productId = product.id, name = product.name, unit = product.unit,
                qty = units.toString(), cost = Money.fromMinor(product.costPrice).toPlainString(),
            )
        )
    }

    /** null when any number is missing or malformed (cost is required, discounts and paid default to 0). */
    fun parsed(): ParsedEntry? {
        val out = ArrayList<PurchaseLine>()
        for (l in lines) {
            val qty = Quantities.parseMilli(l.qty)?.takeIf { it > 0L } ?: return null
            val cost = Money.parseToMinor(l.cost) ?: return null
            val discount = Money.parseToMinor(l.discount.ifBlank { "0" }) ?: return null
            out += PurchaseLine(
                productId = l.productId, qtyMilli = qty, unitCost = cost, discount = discount,
                expiryDate = l.expiryUtcMillis?.let { ExpiryDates.toStored(it) },
                batchNo = l.batchNo.trim().takeIf { it.isNotEmpty() },
            )
        }
        val discountAmount = Money.parseToMinor(this.invoiceDiscount.ifBlank { "0" }) ?: return null
        val paidAmount = Money.parseToMinor(this.paid.ifBlank { "0" }) ?: return null
        return ParsedEntry(out, discountAmount, paidAmount)
    }

    /** Live totals, or null when the entry is empty/invalid (the screen then shows 0 and blocks approval). */
    fun totals(): PurchaseTotals? {
        val p = parsed() ?: return null
        return try {
            PurchaseCalculator.totals(p.lines, p.invoiceDiscount)
        } catch (e: PurchaseException) {
            null
        }
    }

    fun toDraft(uuid: String, supplierId: Long, userId: Long, isReturn: Boolean): PurchaseDraft? {
        val p = parsed() ?: return null
        return PurchaseDraft(
            uuid = uuid, supplierId = supplierId, userId = userId, lines = p.lines,
            invoiceDiscount = p.invoiceDiscount, paid = p.paid, notes = notes.trim().ifEmpty { null }, isReturn = isReturn,
        )
    }
}
