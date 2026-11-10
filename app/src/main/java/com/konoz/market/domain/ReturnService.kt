package com.konoz.market.domain

import com.konoz.market.core.Allocation
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.ReturnCondition
import com.konoz.market.data.entity.ReturnItem
import com.konoz.market.data.entity.SalesItem
import com.konoz.market.data.entity.SalesReturn
import com.konoz.market.data.entity.StockBatch

/**
 * Customer returns of a COMPLETED sales invoice. One transaction per call; safe to retry with the same uuid.
 *
 * Refund rule: what the customer actually paid for a line = the line's share of the invoice total (after item
 * discounts, invoice discount and tax; shares add up exactly to the invoice total). Returning n of q units refunds
 * portion(share, n, q), computed cumulatively so that returning everything over several returns refunds EXACTLY
 * the paid share, never a piastre more.
 *
 * Stock rule:
 *  - RESELLABLE goes back into stock as a new batch (cost = the cost snapshot of the sale, no expiry date)
 *    with a RETURN_IN movement.
 *  - DAMAGED is not restocked: a RETURN_IN and a DAMAGED movement of the same quantity are written (net zero) so the
 *    write-off stays visible in the ledger.
 * The original invoice is never modified; reports subtract returns from sales.
 */
class ReturnService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val sales get() = db.sales()
    private val stock get() = db.stock()

    /** Looks an invoice up by its number for the return screen. Null if missing or not completed. */
    suspend fun loadForReturn(invoiceNumber: Long): ReturnableInvoice? {
        val inv = sales.getByNumber(invoiceNumber) ?: return null
        if (inv.status != InvoiceStatus.COMPLETED) return null
        return ReturnableInvoice(inv, sales.getItems(inv.id).map { ReturnableLine(it, sales.returnedQty(it.id)) })
    }

    private class Planned(val item: SalesItem, val qtyMilli: Long, val condition: ReturnCondition, val refund: Long)

    suspend fun createReturn(draft: ReturnDraft): ReturnResult = db.tx {
        val now = clock()
        val existing = sales.getReturnByUuid(draft.uuid)
        if (existing != null) return@tx ReturnResult(existing.id, existing.refundAmount, duplicate = true)

        if (draft.lines.isEmpty()) throw ReturnException.EmptyReturn()
        val invoice = sales.getInvoice(draft.salesInvoiceId) ?: throw ReturnException.InvoiceNotFound()
        if (invoice.status != InvoiceStatus.COMPLETED) throw ReturnException.InvoiceNotCompleted()

        val items = sales.getItems(invoice.id)
        val paidShares = Allocation.proportional(invoice.total, items.map { it.lineTotal })
        val shareById = items.indices.associate { items[it].id to paidShares[it] }
        val byId = items.associateBy { it.id }

        // ---- phase 1: validate and compute refunds (nothing is written yet) ----
        val pendingByItem = HashMap<Long, Long>()
        val planned = ArrayList<Planned>()
        for (line in draft.lines) {
            val item = byId[line.salesItemId] ?: throw ReturnException.ItemNotInInvoice(line.salesItemId)
            if (line.qtyMilli <= 0L) throw ReturnException.InvalidQuantity(item.id)
            val product = db.products().getById(item.productId)
            if (product != null && !product.unit.isFractional && line.qtyMilli % 1000L != 0L) {
                throw ReturnException.InvalidQuantity(item.id)
            }
            val alreadyReturned = sales.returnedQty(item.id) + (pendingByItem[item.id] ?: 0L)
            val available = item.qtyMilli - alreadyReturned
            if (line.qtyMilli > available) throw ReturnException.QuantityExceeds(item.id, available)

            val share = shareById.getValue(item.id)
            val refund = Allocation.portion(share, alreadyReturned + line.qtyMilli, item.qtyMilli) -
                Allocation.portion(share, alreadyReturned, item.qtyMilli)
            pendingByItem[item.id] = (pendingByItem[item.id] ?: 0L) + line.qtyMilli
            planned += Planned(item, line.qtyMilli, line.condition, refund)
        }
        val totalRefund = planned.sumOf { it.refund }

        // ---- phase 2: write ----
        val returnId = sales.insertReturn(
            SalesReturn(
                uuid = draft.uuid, salesInvoiceId = invoice.id, userId = draft.userId, reason = draft.reason,
                refundAmount = totalRefund, refundMethod = draft.refundMethod,
                shiftId = db.shifts().getOpenShift()?.id, createdAt = now,
            )
        )
        sales.insertReturnItems(
            planned.map { ReturnItem(returnId = returnId, salesItemId = it.item.id, productId = it.item.productId, qtyMilli = it.qtyMilli, refundAmount = it.refund, condition = it.condition) }
        )
        for ((index, p) in planned.withIndex()) {
            val key = "SRETURN:${draft.uuid}:$index"
            when (p.condition) {
                ReturnCondition.RESELLABLE -> {
                    if (stock.movementExists("$key:in") > 0) continue
                    val batchId = stock.insertBatch(
                        StockBatch(productId = p.item.productId, qtyMilli = p.qtyMilli, unitCost = p.item.unitCostAtSale, receivedAt = now)
                    )
                    stock.insertMovement(
                        InventoryMovement(
                            productId = p.item.productId, batchId = batchId, type = MovementType.RETURN_IN, qtyMilli = p.qtyMilli,
                            unitCost = p.item.unitCostAtSale, refType = "SALES_RETURN", refId = returnId, reason = draft.reason,
                            userId = draft.userId, idempotencyKey = "$key:in", createdAt = now,
                        )
                    )
                    db.products().adjustQty(p.item.productId, p.qtyMilli)
                }
                ReturnCondition.DAMAGED -> {
                    if (stock.movementExists("$key:in") > 0) continue
                    stock.insertMovement(
                        InventoryMovement(
                            productId = p.item.productId, type = MovementType.RETURN_IN, qtyMilli = p.qtyMilli,
                            unitCost = p.item.unitCostAtSale, refType = "SALES_RETURN", refId = returnId, reason = draft.reason,
                            userId = draft.userId, idempotencyKey = "$key:in", createdAt = now,
                        )
                    )
                    stock.insertMovement(
                        InventoryMovement(
                            productId = p.item.productId, type = MovementType.DAMAGED, qtyMilli = -p.qtyMilli,
                            unitCost = p.item.unitCostAtSale, refType = "SALES_RETURN", refId = returnId, reason = draft.reason,
                            userId = draft.userId, idempotencyKey = "$key:dmg", createdAt = now,
                        )
                    )
                }
            }
        }
        db.audit().insert(
            AuditLog(
                userId = draft.userId, action = "SALES_RETURN", entity = "sales_return", entityId = returnId,
                details = "invoice=${invoice.number} refund=$totalRefund method=${draft.refundMethod}", createdAt = now,
            )
        )
        ReturnResult(returnId, totalRefund)
    }
}
