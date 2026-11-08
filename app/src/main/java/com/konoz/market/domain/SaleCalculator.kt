package com.konoz.market.domain

import com.konoz.market.core.Money

data class SaleTotals(
    val subtotal: Long,
    val itemDiscountTotal: Long,
    val invoiceDiscount: Long,
    val taxAmount: Long,
    val total: Long,
)

/**
 * Pure function, no database: subtotal -> item discounts -> invoice discount -> tax on the remainder.
 * Tax is calculated on the discounted amount.
 */
object SaleCalculator {

    fun lineGross(line: CartLine): Long = Money.lineTotal(line.unitPrice, line.qtyMilli)

    fun totals(lines: List<CartLine>, invoiceDiscount: Long, taxBasisPoints: Int): SaleTotals {
        if (lines.isEmpty()) throw SaleException.EmptyCart()
        var subtotal = 0L
        var itemDiscounts = 0L
        for (l in lines) {
            if (l.qtyMilli <= 0L) throw SaleException.InvalidQuantity(l.productId)
            val gross = lineGross(l)
            if (l.discount < 0L || l.discount > gross) throw SaleException.InvalidDiscount()
            subtotal += gross
            itemDiscounts += l.discount
        }
        val afterItems = subtotal - itemDiscounts
        if (invoiceDiscount < 0L || invoiceDiscount > afterItems) throw SaleException.InvalidDiscount()
        val taxable = afterItems - invoiceDiscount
        val tax = if (taxBasisPoints > 0) Money.percentOf(taxable, taxBasisPoints) else 0L
        return SaleTotals(subtotal, itemDiscounts, invoiceDiscount, tax, taxable + tax)
    }
}
