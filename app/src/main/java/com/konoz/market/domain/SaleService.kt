package com.konoz.market.domain

import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.Payment
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesItem
import com.konoz.market.data.entity.StockBatch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Sales posting. Every public function runs in ONE database transaction: either the invoice, its items,
 * payments, stock movements, batch quantities and audit row are all written, or nothing is.
 */
class SaleService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val sales get() = db.sales()
    private val stock get() = db.stock()
    private val products get() = db.products()

    /** Saves the cart as a HELD invoice. No stock effect, no invoice number consumed. Re-holding the same uuid replaces it. */
    suspend fun holdInvoice(draft: SaleDraft): Long = db.tx {
        val totals = SaleCalculator.totals(draft.lines, draft.invoiceDiscount, draft.taxBasisPoints)
        val now = clock()
        val existing = sales.getByUuid(draft.uuid)
        if (existing != null && existing.status != InvoiceStatus.HELD) throw SaleException.AlreadyFinalized()

        val invoice = SalesInvoice(
            id = existing?.id ?: 0,
            uuid = draft.uuid,
            number = null,
            userId = draft.userId,
            shiftId = draft.shiftId,
            status = InvoiceStatus.HELD,
            subtotal = totals.subtotal,
            itemDiscountTotal = totals.itemDiscountTotal,
            invoiceDiscount = totals.invoiceDiscount,
            taxBasisPoints = draft.taxBasisPoints,
            taxAmount = totals.taxAmount,
            total = totals.total,
            notes = draft.notes,
            createdAt = existing?.createdAt ?: now,
        )
        val id = if (existing == null) {
            sales.insertInvoice(invoice)
        } else {
            sales.updateInvoice(invoice)
            sales.deleteItems(existing.id)
            existing.id
        }
        val items = draft.lines.map { line ->
            val product = products.getById(line.productId) ?: throw SaleException.ProductUnavailable(line.productId)
            SalesItem(
                invoiceId = id,
                productId = product.id,
                productName = product.name,
                barcode = line.barcode,
                qtyMilli = line.qtyMilli,
                unitPrice = line.unitPrice,
                unitCostAtSale = product.costPrice, // provisional; final cost is fixed at completion
                discount = line.discount,
                lineTotal = SaleCalculator.lineGross(line) - line.discount,
            )
        }
        sales.insertItems(items)
        id
    }

    /** Rebuilds the cart of a HELD invoice, or null if it does not exist / is not held. */
    suspend fun resumeInvoice(invoiceId: Long): SaleDraft? {
        val inv = sales.getInvoice(invoiceId) ?: return null
        if (inv.status != InvoiceStatus.HELD) return null
        val lines = sales.getItems(invoiceId).map {
            CartLine(it.productId, it.qtyMilli, it.unitPrice, it.discount, it.barcode)
        }
        return SaleDraft(
            uuid = inv.uuid,
            userId = inv.userId,
            shiftId = inv.shiftId,
            lines = lines,
            invoiceDiscount = inv.invoiceDiscount,
            taxBasisPoints = inv.taxBasisPoints,
            notes = inv.notes,
        )
    }

    suspend fun discardHeld(invoiceId: Long) = sales.deleteHeld(invoiceId)

    /**
     * Completes a sale. Safe to retry with the same draft.uuid: a second call returns the first result
     * with duplicate = true and posts nothing.
     */
    suspend fun completeSale(
        draft: SaleDraft,
        payments: List<PaymentInput>,
        options: SaleOptions = SaleOptions(),
    ): SaleResult = db.tx {
        val now = clock()
        val existing = sales.getByUuid(draft.uuid)
        if (existing != null) {
            when (existing.status) {
                InvoiceStatus.COMPLETED -> return@tx resultOf(existing, duplicate = true)
                InvoiceStatus.VOIDED -> throw SaleException.AlreadyFinalized()
                InvoiceStatus.HELD -> Unit
            }
        }

        val totals = SaleCalculator.totals(draft.lines, draft.invoiceDiscount, draft.taxBasisPoints)

        // ---- payment validation ----
        if (payments.any { it.amount <= 0L }) throw SaleException.InvalidPayment()
        val paid = payments.sumOf { it.amount }
        val cashGiven = payments.filter { it.method == PaymentMethod.CASH }.sumOf { it.amount }
        val nonCash = paid - cashGiven
        if (paid < totals.total) throw SaleException.InsufficientPayment(totals.total, paid)
        if (nonCash > totals.total) throw SaleException.NonCashOverpayment() // change can only come from cash
        val change = paid - totals.total

        // ---- invoice row ----
        val base = SalesInvoice(
            id = existing?.id ?: 0,
            uuid = draft.uuid,
            userId = draft.userId,
            shiftId = draft.shiftId,
            status = InvoiceStatus.HELD,
            createdAt = existing?.createdAt ?: now,
        )
        val invoiceId = if (existing == null) {
            sales.insertInvoice(base)
        } else {
            sales.deleteItems(existing.id)
            sales.deletePayments(existing.id)
            existing.id
        }

        // ---- items + stock (FEFO) ----
        var totalCost = 0L
        val items = ArrayList<SalesItem>()
        for ((index, line) in draft.lines.withIndex()) {
            val product = products.getById(line.productId)
            if (product == null || !product.isActive) throw SaleException.ProductUnavailable(line.productId)
            if (!product.unit.isFractional && line.qtyMilli % 1000L != 0L) {
                throw SaleException.InvalidQuantity(line.productId)
            }

            val allocations = allocate(product, line.qtyMilli, now, options)
            var lineCost = 0L
            for ((aIndex, a) in allocations.withIndex()) {
                val posted = stock.insertMovement(
                    InventoryMovement(
                        productId = product.id,
                        batchId = a.batch?.id,
                        type = MovementType.SALE,
                        qtyMilli = -a.qtyMilli,
                        unitCost = a.unitCost,
                        refType = "SALE",
                        refId = invoiceId,
                        userId = draft.userId,
                        idempotencyKey = "SALE:${draft.uuid}:$index:$aIndex",
                        createdAt = now,
                    )
                )
                if (posted == -1L) continue // already posted once: never apply twice
                a.batch?.let { stock.updateBatch(it.copy(qtyMilli = it.qtyMilli - a.qtyMilli)) }
                lineCost += Money.costOf(a.unitCost, a.qtyMilli)
            }
            products.adjustQty(product.id, -line.qtyMilli)
            totalCost += lineCost

            val unitCostAtSale = BigDecimal(lineCost).multiply(BigDecimal(1000))
                .divide(BigDecimal(line.qtyMilli), 0, RoundingMode.HALF_UP).toLong()
            items += SalesItem(
                invoiceId = invoiceId,
                productId = product.id,
                productName = product.name,
                barcode = line.barcode,
                qtyMilli = line.qtyMilli,
                unitPrice = line.unitPrice,
                unitCostAtSale = unitCostAtSale,
                discount = line.discount,
                lineTotal = SaleCalculator.lineGross(line) - line.discount,
            )
        }
        sales.insertItems(items)

        // ---- payments: stored NET (cash minus change) so SUM(payments) == invoice total ----
        val stored = ArrayList<Payment>()
        for (method in PaymentMethod.values()) {
            var amount = payments.filter { it.method == method }.sumOf { it.amount }
            if (method == PaymentMethod.CASH) amount -= change
            if (amount > 0L) stored += Payment(
                invoiceId = invoiceId, shiftId = draft.shiftId, method = method, amount = amount, createdAt = now,
            )
        }
        sales.insertPayments(stored)

        // ---- finalize ----
        val number = sales.nextNumber()
        val completed = base.copy(
            id = invoiceId,
            number = number,
            status = InvoiceStatus.COMPLETED,
            subtotal = totals.subtotal,
            itemDiscountTotal = totals.itemDiscountTotal,
            invoiceDiscount = totals.invoiceDiscount,
            taxBasisPoints = draft.taxBasisPoints,
            taxAmount = totals.taxAmount,
            total = totals.total,
            totalCost = totalCost, // COGS snapshot: never recomputed from today's product cost
            paidTotal = paid,
            changeDue = change,
            notes = draft.notes,
            completedAt = now,
            stockPosted = true,
        )
        sales.updateInvoice(completed)
        db.audit().insert(
            AuditLog(
                userId = draft.userId, action = "SALE_COMPLETED", entity = "sales_invoice", entityId = invoiceId,
                details = "number=$number total=${totals.total} cost=$totalCost", createdAt = now,
            )
        )
        resultOf(completed, duplicate = false)
    }

    private fun resultOf(i: SalesInvoice, duplicate: Boolean) = SaleResult(
        invoiceId = i.id, number = i.number ?: 0, total = i.total, paidTotal = i.paidTotal,
        changeDue = i.changeDue, totalCost = i.totalCost, duplicate = duplicate,
    )

    private class Alloc(val batch: StockBatch?, val qtyMilli: Long, val unitCost: Long)

    /** FEFO allocation. Expired batches are skipped unless the cashier explicitly overrides. */
    private suspend fun allocate(product: Product, qty: Long, now: Long, opts: SaleOptions): List<Alloc> {
        val batches = stock.fefoBatches(product.id)
        val fresh = batches.filter { it.expiryDate == null || it.expiryDate > now }
        val expired = batches.filter { it.expiryDate != null && it.expiryDate <= now }

        val out = ArrayList<Alloc>()
        var remaining = qty
        for (b in fresh) {
            if (remaining == 0L) break
            val take = minOf(b.qtyMilli, remaining)
            out += Alloc(b, take, b.unitCost)
            remaining -= take
        }
        if (remaining > 0L && expired.isNotEmpty()) {
            if (!opts.allowExpired) throw SaleException.ExpiredProduct(product.id)
            for (b in expired) {
                if (remaining == 0L) break
                val take = minOf(b.qtyMilli, remaining)
                out += Alloc(b, take, b.unitCost)
                remaining -= take
            }
        }
        if (remaining > 0L) {
            if (!opts.allowNegativeStock) throw SaleException.InsufficientStock(product.id, remaining)
            out += Alloc(null, remaining, product.costPrice) // untracked stock goes negative
        }
        return out
    }
}
