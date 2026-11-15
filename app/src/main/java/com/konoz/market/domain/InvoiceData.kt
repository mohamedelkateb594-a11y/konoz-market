package com.konoz.market.domain

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.PaymentMethod

class InvoiceLine(val name: String, val qtyMilli: Long, val unitPrice: Long, val discount: Long, val total: Long)

/** [amount] is what the customer handed over with this method (cash includes the change given back). */
class InvoicePayment(val method: PaymentMethod, val amount: Long)

/** Everything a printed or shared invoice shows, read once from the saved invoice. Money in piastres. */
class InvoiceData(
    val number: Long,
    val dateMillis: Long,
    val cashier: String?,
    val lines: List<InvoiceLine>,
    /** Sum of the line totals (after item discounts). */
    val subtotal: Long,
    val invoiceDiscount: Long,
    val taxBasisPoints: Int,
    val taxAmount: Long,
    val total: Long,
    val payments: List<InvoicePayment>,
    val paid: Long,
    val changeDue: Long,
    val notes: String?,
)

/** Reads completed invoices for display. Held and unknown invoices give null. */
class InvoiceLoader(private val db: KonozDatabase) {

    suspend fun load(invoiceId: Long): InvoiceData? = db.tx {
        val invoice = db.sales().getInvoice(invoiceId) ?: return@tx null
        val number = invoice.number ?: return@tx null
        if (invoice.status != InvoiceStatus.COMPLETED) return@tx null

        val lines = db.sales().getItems(invoiceId).map { InvoiceLine(it.productName, it.qtyMilli, it.unitPrice, it.discount, it.lineTotal) }
        // Payments are stored net of change; show the cash line as handed over so the lines add up to the amount paid.
        var changeLeft = invoice.changeDue
        val payments = db.sales().getPayments(invoiceId).map { p ->
            if (p.method == PaymentMethod.CASH && changeLeft > 0L) {
                val tendered = p.amount + changeLeft
                changeLeft = 0L
                InvoicePayment(p.method, tendered)
            } else {
                InvoicePayment(p.method, p.amount)
            }
        }
        InvoiceData(
            number = number,
            dateMillis = invoice.completedAt ?: invoice.createdAt,
            cashier = db.users().getById(invoice.userId)?.name,
            lines = lines,
            subtotal = lines.sumOf { it.total },
            invoiceDiscount = invoice.invoiceDiscount,
            taxBasisPoints = invoice.taxBasisPoints,
            taxAmount = invoice.taxAmount,
            total = invoice.total,
            payments = payments,
            paid = payments.sumOf { it.amount },
            changeDue = invoice.changeDue,
            notes = invoice.notes?.trim()?.ifEmpty { null },
        )
    }
}
