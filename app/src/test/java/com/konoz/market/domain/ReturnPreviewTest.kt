package com.konoz.market.domain

import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesItem
import com.konoz.market.ui.alerts.AlertMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ReturnPreviewTest {

    private fun invoice(total: Long) = SalesInvoice(id = 1, uuid = "u", userId = 1, status = InvoiceStatus.COMPLETED, total = total, createdAt = 0)

    private fun item(id: Long, qty: Long, lineTotal: Long) = SalesItem(
        id = id, invoiceId = 1, productId = id, productName = "p$id", qtyMilli = qty, unitPrice = 0, unitCostAtSale = 0, lineTotal = lineTotal,
    )

    @Test fun partial_returns_add_up_to_the_paid_share() {
        val first = ReturnableInvoice(invoice(4050), listOf(ReturnableLine(item(1, 3000, 4500), 0)))
        assertEquals(1350L, ReturnPreview.refund(first, mapOf(1L to 1000L)))

        val after = ReturnableInvoice(invoice(4050), listOf(ReturnableLine(item(1, 3000, 4500), 1000)))
        assertEquals(2700L, ReturnPreview.refund(after, mapOf(1L to 2000L)))
    }

    @Test fun two_items_share_the_invoice_total_exactly() {
        // lines of 10.00 and 20.00, invoice total 29.99 (a 0.01 discount): shares must add up to 2999
        val inv = ReturnableInvoice(invoice(2999), listOf(ReturnableLine(item(1, 1000, 1000), 0), ReturnableLine(item(2, 1000, 2000), 0)))
        assertEquals(2999L, ReturnPreview.refund(inv, mapOf(1L to 1000L, 2L to 1000L)))
    }

    @Test fun nothing_picked_means_no_refund() {
        val inv = ReturnableInvoice(invoice(1000), listOf(ReturnableLine(item(1, 1000, 1000), 0)))
        assertEquals(0L, ReturnPreview.refund(inv, emptyMap()))
    }

    @Test fun suggested_purchase_quantity_reaches_twice_the_alert_level_and_is_at_least_one_unit() {
        assertEquals(8000L, AlertMath.suggestedMilli(2000, 5000)) // 2 x 5 - 2 = 8
        assertEquals(1000L, AlertMath.suggestedMilli(0, 0)) // no threshold: still suggest one unit
        assertEquals(1000L, AlertMath.suggestedMilli(9000, 5000)) // already above twice the level
    }
}
