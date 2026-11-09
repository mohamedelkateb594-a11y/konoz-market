package com.konoz.market.domain

import com.konoz.market.core.Money
import com.konoz.market.data.entity.PurchaseInvoice
import java.math.BigDecimal
import java.math.RoundingMode

/** Pure purchase math, no database. */
object PurchaseCalculator {

    fun totals(lines: List<PurchaseLine>, invoiceDiscount: Long): PurchaseTotals {
        if (lines.isEmpty()) throw PurchaseException.EmptyLines()
        val nets = lines.map { l ->
            if (l.qtyMilli <= 0L) throw PurchaseException.InvalidQuantity(l.productId)
            if (l.unitCost < 0L) throw PurchaseException.InvalidAmount()
            val gross = Money.lineTotal(l.unitCost, l.qtyMilli)
            if (l.discount < 0L || l.discount > gross) throw PurchaseException.InvalidAmount()
            gross - l.discount
        }
        val subtotal = nets.sum()
        if (invoiceDiscount < 0L || invoiceDiscount > subtotal) throw PurchaseException.InvalidAmount()
        return PurchaseTotals(subtotal, invoiceDiscount, subtotal - invoiceDiscount, nets)
    }

    /**
     * Spreads the invoice-level discount over the lines in proportion to their value, using the
     * largest-remainder method so the shares add up EXACTLY to the discount (no lost piastres).
     * Returns each line's effective cost; their sum equals subtotal - invoiceDiscount.
     * These are the figures batch costs (and therefore COGS) are based on.
     */
    fun effectiveLineCosts(lineNets: List<Long>, invoiceDiscount: Long): List<Long> {
        val subtotal = lineNets.sum()
        if (invoiceDiscount == 0L || subtotal == 0L) return lineNets

        val exact = lineNets.map {
            BigDecimal(invoiceDiscount).multiply(BigDecimal(it)).divide(BigDecimal(subtotal), 12, RoundingMode.DOWN)
        }
        val shares = exact.map { it.setScale(0, RoundingMode.DOWN).toLong() }.toMutableList()
        var remaining = invoiceDiscount - shares.sum()
        val byRemainder = exact.indices.sortedByDescending { exact[it].subtract(BigDecimal(shares[it])) }
        for (i in byRemainder) {
            if (remaining == 0L) break
            shares[i] = shares[i] + 1
            remaining--
        }
        return lineNets.mapIndexed { i, net -> net - shares[i] }
    }

    /** Cost per base unit (piastres) for a line total, rounded HALF_UP. */
    fun unitCostOf(lineCost: Long, qtyMilli: Long): Long =
        BigDecimal(lineCost).multiply(BigDecimal(1000)).divide(BigDecimal(qtyMilli), 0, RoundingMode.HALF_UP).toLong()
}

data class StatementLine(
    val invoice: PurchaseInvoice,
    /** Effect on what we owe the supplier: + for purchases, - for returns (their unsettled part). */
    val delta: Long,
    val balanceAfter: Long,
)

/**
 * Supplier account statement. Balance = what we owe the supplier = sum of the unsettled part (remaining) of
 * approved purchases minus the unsettled part of approved returns. A negative balance means the supplier owes us.
 * Later settlements change `remaining` on their invoice, so running balances reflect today's state.
 */
object SupplierStatement {
    fun build(invoices: List<PurchaseInvoice>): List<StatementLine> {
        var balance = 0L
        return invoices
            .sortedWith(compareBy<PurchaseInvoice>({ it.approvedAt ?: it.createdAt }, { it.id }))
            .map { inv ->
                val delta = if (inv.isReturn) -inv.remaining else inv.remaining
                balance += delta
                StatementLine(inv, delta, balance)
            }
    }
}
