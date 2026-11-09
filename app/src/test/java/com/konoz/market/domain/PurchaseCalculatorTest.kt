package com.konoz.market.domain

import com.konoz.market.data.entity.PurchaseInvoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseCalculatorTest {

    private fun line(qty: Long = 1000, cost: Long = 1000, discount: Long = 0) =
        PurchaseLine(productId = 1, qtyMilli = qty, unitCost = cost, discount = discount)

    @Test fun totals_apply_line_and_invoice_discounts() {
        // 10 x 10.00 = 100.00, line discount 5.00 -> 95.00; second line 2 x 3.50 = 7.00; invoice discount 2.00
        val t = PurchaseCalculator.totals(listOf(line(10000, 1000, 500), line(2000, 350)), 200)
        assertEquals(listOf(9500L, 700L), t.lineNets)
        assertEquals(10200L, t.subtotal)
        assertEquals(10000L, t.total)
    }

    @Test fun invalid_inputs_are_rejected() {
        assertThrows(PurchaseException.EmptyLines::class.java) { PurchaseCalculator.totals(emptyList(), 0) }
        assertThrows(PurchaseException.InvalidQuantity::class.java) { PurchaseCalculator.totals(listOf(line(qty = 0)), 0) }
        assertThrows(PurchaseException.InvalidAmount::class.java) { PurchaseCalculator.totals(listOf(line(1000, 1000, 1001)), 0) }
        assertThrows(PurchaseException.InvalidAmount::class.java) { PurchaseCalculator.totals(listOf(line()), 1001) }
    }

    @Test fun invoice_discount_is_spread_exactly_with_no_lost_piastres() {
        // 3 equal lines, 1.00 discount: shares 34/33/33 so the effective costs add up to 2900 exactly
        val eff = PurchaseCalculator.effectiveLineCosts(listOf(1000L, 1000L, 1000L), 100)
        assertEquals(listOf(966L, 967L, 967L), eff)
        assertEquals(2900L, eff.sum())
    }

    @Test fun spreading_never_makes_a_line_negative_even_for_a_full_discount() {
        val eff = PurchaseCalculator.effectiveLineCosts(listOf(1L, 1L, 1L, 7L), 10)
        assertEquals(0L, eff.sum())
        assertTrue(eff.all { it >= 0L })
    }

    @Test fun no_discount_returns_the_nets_unchanged() {
        assertEquals(listOf(500L, 700L), PurchaseCalculator.effectiveLineCosts(listOf(500L, 700L), 0))
    }

    @Test fun unit_cost_is_rounded_half_up() {
        // 100.00 for 3 pieces = 33.333.. -> 33.33
        assertEquals(3333L, PurchaseCalculator.unitCostOf(10000, 3000))
    }

    private fun invoice(id: Long, remaining: Long, isReturn: Boolean, approvedAt: Long) = PurchaseInvoice(
        id = id, uuid = "u$id", supplierId = 1, userId = 1, isReturn = isReturn, remaining = remaining,
        createdAt = approvedAt, approvedAt = approvedAt,
    )

    @Test fun statement_orders_by_date_and_keeps_a_running_balance_where_returns_reduce_it() {
        val shuffled = listOf(
            invoice(3, 1000, false, 30),
            invoice(1, 4000, false, 10),
            invoice(2, 500, true, 20),
        )
        val lines = SupplierStatement.build(shuffled)
        assertEquals(listOf(1L, 2L, 3L), lines.map { it.invoice.id })
        assertEquals(listOf(4000L, -500L, 1000L), lines.map { it.delta })
        assertEquals(listOf(4000L, 3500L, 4500L), lines.map { it.balanceAfter })
    }
}
