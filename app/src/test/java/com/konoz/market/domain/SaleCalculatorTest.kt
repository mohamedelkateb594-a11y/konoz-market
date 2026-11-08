package com.konoz.market.domain

import com.konoz.market.data.entity.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaleCalculatorTest {

    private fun line(price: Long, qtyMilli: Long, discount: Long = 0) =
        CartLine(productId = 1, qtyMilli = qtyMilli, unitPrice = price, discount = discount)

    @Test fun simple_total() {
        val t = SaleCalculator.totals(listOf(line(1000, 2000), line(550, 1000)), 0, 0)
        assertEquals(2550L, t.subtotal)
        assertEquals(2550L, t.total)
    }

    @Test fun discounts_then_tax_on_discounted_amount() {
        // gross 100.00, item discount 10.00, invoice discount 5.00 -> taxable 85.00, tax 14% = 11.90
        val t = SaleCalculator.totals(listOf(line(10000, 1000, discount = 1000)), 500, 1400)
        assertEquals(10000L, t.subtotal)
        assertEquals(1000L, t.itemDiscountTotal)
        assertEquals(1190L, t.taxAmount)
        assertEquals(9690L, t.total)
    }

    @Test fun weighted_line() {
        val t = SaleCalculator.totals(listOf(line(1230, 2500)), 0, 0)
        assertEquals(3075L, t.total)
    }

    @Test fun invalid_inputs_are_rejected() {
        assertThrows(SaleException.EmptyCart::class.java) { SaleCalculator.totals(emptyList(), 0, 0) }
        assertThrows(SaleException.InvalidQuantity::class.java) { SaleCalculator.totals(listOf(line(100, 0)), 0, 0) }
        assertThrows(SaleException.InvalidDiscount::class.java) { SaleCalculator.totals(listOf(line(100, 1000, discount = 101)), 0, 0) }
        assertThrows(SaleException.InvalidDiscount::class.java) { SaleCalculator.totals(listOf(line(100, 1000)), 101, 0) }
    }

    @Test fun payment_method_enum_has_expected_values() {
        assertEquals(listOf("CASH", "CARD", "WALLET"), PaymentMethod.values().map { it.name })
    }
}
