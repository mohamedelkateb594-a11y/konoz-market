package com.konoz.market.domain

import com.konoz.market.core.Allocation

/**
 * Refund preview for the return screen. Same math as ReturnService (kept in sync by ReturnPreviewTest):
 * each line's paid share of the invoice total, refunded cumulatively so partial returns add up exactly.
 */
object ReturnPreview {
    /** picks: salesItemId -> quantity (milli) the cashier wants to return now. */
    fun refund(invoice: ReturnableInvoice, picks: Map<Long, Long>): Long {
        val shares = Allocation.proportional(invoice.invoice.total, invoice.lines.map { it.item.lineTotal })
        var total = 0L
        invoice.lines.forEachIndexed { i, line ->
            val q = picks[line.item.id] ?: 0L
            if (q > 0L) {
                total += Allocation.portion(shares[i], line.returnedMilli + q, line.item.qtyMilli) -
                    Allocation.portion(shares[i], line.returnedMilli, line.item.qtyMilli)
            }
        }
        return total
    }
}
