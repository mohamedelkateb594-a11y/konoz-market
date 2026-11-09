package com.konoz.market.domain

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.PurchaseInvoice
import com.konoz.market.data.entity.PurchaseItem
import com.konoz.market.data.entity.PurchaseStatus
import com.konoz.market.data.entity.StockBatch

/**
 * Purchases from suppliers and returns to suppliers. Every public function runs in ONE transaction.
 *
 *  - saveDraft: stores a DRAFT (no stock effect, no invoice number).
 *  - approve (purchase): one new StockBatch per line + PURCHASE movement + product quantity, and the product's
 *    cost price becomes the latest purchase cost. Safe to retry with the same uuid (duplicate = true).
 *  - approve (return, isReturn = true): takes the goods out of stock FEFO (earliest expiry first, expired included),
 *    PURCHASE_RETURN movements; refuses to go below zero stock.
 *  - settle: pays an approved purchase (or collects the refund of an approved return) over time.
 *
 * Purchases and returns share one invoice-number sequence. The invoice discount is spread over the lines so
 * batch costs (and COGS) include it.
 */
class PurchaseService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val purchases get() = db.purchases()
    private val stock get() = db.stock()
    private val products get() = db.products()

    suspend fun saveDraft(draft: PurchaseDraft): Long = db.tx {
        val totals = PurchaseCalculator.totals(draft.lines, draft.invoiceDiscount)
        val existing = purchases.getByUuid(draft.uuid)
        if (existing != null && existing.status != PurchaseStatus.DRAFT) throw PurchaseException.AlreadyFinalized()
        if (db.suppliers().getById(draft.supplierId) == null) throw PurchaseException.SupplierNotFound()

        val paid = draft.paid.coerceIn(0L, totals.total)
        val invoice = PurchaseInvoice(
            id = existing?.id ?: 0,
            uuid = draft.uuid,
            number = null,
            supplierId = draft.supplierId,
            userId = draft.userId,
            status = PurchaseStatus.DRAFT,
            subtotal = totals.subtotal,
            discount = totals.invoiceDiscount,
            total = totals.total,
            paid = paid,
            remaining = totals.total - paid,
            isReturn = draft.isReturn,
            notes = draft.notes,
            createdAt = existing?.createdAt ?: clock(),
        )
        val id = if (existing == null) {
            purchases.insertInvoice(invoice)
        } else {
            purchases.updateInvoice(invoice)
            purchases.deleteItems(existing.id)
            existing.id
        }
        purchases.insertItems(itemsOf(id, draft.lines, totals.lineNets))
        id
    }

    suspend fun loadDraft(invoiceId: Long): PurchaseDraft? {
        val inv = purchases.getInvoice(invoiceId) ?: return null
        if (inv.status != PurchaseStatus.DRAFT) return null
        val lines = purchases.getItems(invoiceId).map {
            PurchaseLine(it.productId, it.qtyMilli, it.unitCost, it.discount, it.expiryDate, it.batchNo)
        }
        return PurchaseDraft(inv.uuid, inv.supplierId, inv.userId, lines, inv.discount, inv.paid, inv.notes, inv.isReturn)
    }

    suspend fun cancelDraft(invoiceId: Long) = purchases.deleteDraft(invoiceId)

    suspend fun approve(draft: PurchaseDraft): PurchaseResult = db.tx {
        val now = clock()
        val existing = purchases.getByUuid(draft.uuid)
        if (existing != null) {
            when (existing.status) {
                PurchaseStatus.APPROVED -> return@tx resultOf(existing, duplicate = true)
                PurchaseStatus.CANCELLED -> throw PurchaseException.AlreadyFinalized()
                PurchaseStatus.DRAFT -> Unit
            }
        }

        val totals = PurchaseCalculator.totals(draft.lines, draft.invoiceDiscount)
        if (draft.paid < 0L || draft.paid > totals.total) throw PurchaseException.InvalidAmount()
        if (db.suppliers().getById(draft.supplierId) == null) throw PurchaseException.SupplierNotFound()

        val base = PurchaseInvoice(
            id = existing?.id ?: 0,
            uuid = draft.uuid,
            supplierId = draft.supplierId,
            userId = draft.userId,
            status = PurchaseStatus.DRAFT,
            isReturn = draft.isReturn,
            notes = draft.notes,
            createdAt = existing?.createdAt ?: now,
        )
        val invoiceId = if (existing == null) {
            purchases.insertInvoice(base)
        } else {
            purchases.deleteItems(existing.id)
            existing.id
        }

        val effective = PurchaseCalculator.effectiveLineCosts(totals.lineNets, totals.invoiceDiscount)
        for ((index, line) in draft.lines.withIndex()) {
            val product = products.getById(line.productId) ?: throw PurchaseException.ProductUnavailable(line.productId)
            if (!product.unit.isFractional && line.qtyMilli % 1000L != 0L) throw PurchaseException.InvalidQuantity(line.productId)
            if (draft.isReturn) {
                issueToSupplier(invoiceId, draft, index, line, now)
            } else {
                receive(invoiceId, draft, index, line, effective[index], now)
            }
        }
        purchases.insertItems(itemsOf(invoiceId, draft.lines, totals.lineNets))

        val approved = base.copy(
            id = invoiceId,
            number = purchases.nextNumber(),
            status = PurchaseStatus.APPROVED,
            subtotal = totals.subtotal,
            discount = totals.invoiceDiscount,
            total = totals.total,
            paid = draft.paid,
            remaining = totals.total - draft.paid,
            approvedAt = now,
            stockPosted = true,
        )
        purchases.updateInvoice(approved)
        db.audit().insert(
            AuditLog(
                userId = draft.userId,
                action = if (draft.isReturn) "PURCHASE_RETURN_APPROVED" else "PURCHASE_APPROVED",
                entity = "purchase_invoice", entityId = invoiceId,
                details = "number=${approved.number} supplier=${draft.supplierId} total=${totals.total} paid=${draft.paid}",
                createdAt = now,
            )
        )
        resultOf(approved, duplicate = false)
    }

    /** Pays (purchase) or collects the refund of (return) part of the unsettled amount of an approved invoice. */
    suspend fun settle(invoiceId: Long, amount: Long, userId: Long?): PurchaseResult = db.tx {
        val inv = purchases.getInvoice(invoiceId) ?: throw PurchaseException.InvoiceNotFound()
        if (inv.status != PurchaseStatus.APPROVED) throw PurchaseException.InvoiceNotApproved()
        if (amount <= 0L || amount > inv.remaining) throw PurchaseException.InvalidAmount()
        val updated = inv.copy(paid = inv.paid + amount, remaining = inv.remaining - amount)
        purchases.updateInvoice(updated)
        db.audit().insert(
            AuditLog(
                userId = userId, action = "SUPPLIER_SETTLEMENT", entity = "purchase_invoice", entityId = invoiceId,
                details = "amount=$amount remaining=${updated.remaining}", createdAt = clock(),
            )
        )
        resultOf(updated, duplicate = false)
    }

    suspend fun supplierBalance(supplierId: Long): Long = purchases.supplierBalance(supplierId)

    suspend fun statement(supplierId: Long): List<StatementLine> =
        SupplierStatement.build(purchases.approvedForSupplier(supplierId))

    // ---------- internals ----------
    private suspend fun receive(invoiceId: Long, draft: PurchaseDraft, index: Int, line: PurchaseLine, effectiveCost: Long, now: Long) {
        val key = "PURCHASE:${draft.uuid}:$index"
        if (stock.movementExists(key) > 0) return // already posted once: never apply twice
        val unitCost = PurchaseCalculator.unitCostOf(effectiveCost, line.qtyMilli)
        val batchId = stock.insertBatch(
            StockBatch(
                productId = line.productId, batchNo = line.batchNo, qtyMilli = line.qtyMilli, unitCost = unitCost,
                expiryDate = line.expiryDate, receivedAt = now,
            )
        )
        stock.insertMovement(
            InventoryMovement(
                productId = line.productId, batchId = batchId, type = MovementType.PURCHASE, qtyMilli = line.qtyMilli,
                unitCost = unitCost, refType = "PURCHASE", refId = invoiceId, userId = draft.userId,
                idempotencyKey = key, createdAt = now,
            )
        )
        products.adjustQty(line.productId, line.qtyMilli)
        if (unitCost > 0L) products.setCostPrice(line.productId, unitCost) // cost price = latest purchase cost
    }

    private suspend fun issueToSupplier(invoiceId: Long, draft: PurchaseDraft, index: Int, line: PurchaseLine, now: Long) {
        var remaining = line.qtyMilli
        val takes = ArrayList<Pair<StockBatch, Long>>()
        for (b in stock.fefoBatches(line.productId)) { // earliest expiry first, expired batches included
            if (remaining == 0L) break
            val take = minOf(b.qtyMilli, remaining)
            takes += b to take
            remaining -= take
        }
        if (remaining > 0L) throw PurchaseException.InsufficientStockForReturn(line.productId, remaining)

        for ((aIndex, t) in takes.withIndex()) {
            val (batch, take) = t
            val posted = stock.insertMovement(
                InventoryMovement(
                    productId = line.productId, batchId = batch.id, type = MovementType.PURCHASE_RETURN, qtyMilli = -take,
                    unitCost = batch.unitCost, refType = "PURCHASE_RETURN", refId = invoiceId, userId = draft.userId,
                    idempotencyKey = "PRETURN:${draft.uuid}:$index:$aIndex", createdAt = now,
                )
            )
            if (posted == -1L) continue
            stock.updateBatch(batch.copy(qtyMilli = batch.qtyMilli - take))
        }
        products.adjustQty(line.productId, -line.qtyMilli)
    }

    private fun itemsOf(invoiceId: Long, lines: List<PurchaseLine>, nets: List<Long>): List<PurchaseItem> =
        lines.mapIndexed { i, l ->
            PurchaseItem(
                invoiceId = invoiceId, productId = l.productId, qtyMilli = l.qtyMilli, unitCost = l.unitCost,
                discount = l.discount, lineTotal = nets[i], expiryDate = l.expiryDate, batchNo = l.batchNo,
            )
        }

    private fun resultOf(i: PurchaseInvoice, duplicate: Boolean) = PurchaseResult(
        invoiceId = i.id, number = i.number ?: 0, total = i.total, paid = i.paid, remaining = i.remaining,
        isReturn = i.isReturn, duplicate = duplicate,
    )
}
