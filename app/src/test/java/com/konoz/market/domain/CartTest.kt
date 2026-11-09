package com.konoz.market.domain

import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartTest {

    private fun product(id: Long = 1, price: Long = 1500, unit: UnitType = UnitType.PIECE) =
        Product(id = id, name = "p$id", salePrice = price, unit = unit, createdAt = 0)

    private fun cart() = Cart("c1")

    @Test fun scanning_the_same_product_again_increases_quantity() {
        val c = cart().add(product()).add(product())
        assertEquals(1, c.items.size)
        assertEquals(2000L, c.items[0].qtyMilli)
    }

    @Test fun carton_barcode_adds_units_per_scan() {
        val c = cart().add(product(), units = 12)
        assertEquals(12000L, c.items[0].qtyMilli)
    }

    @Test fun different_products_are_separate_lines() {
        val c = cart().add(product(1)).add(product(2))
        assertEquals(2, c.items.size)
    }

    @Test fun setQty_zero_removes_the_line() {
        val c = cart().add(product()).setQty(1, 0)
        assertTrue(c.isEmpty)
        assertNull(c.totals())
    }

    @Test fun weighted_quantity_total() {
        val c = cart().add(product(price = 1230, unit = UnitType.KG)).setQty(1, 2500)
        assertEquals(3075L, c.totals()!!.total)
    }

    @Test fun line_discount_is_clamped_to_the_line_total() {
        val c = cart().add(product(price = 1000)).setLineDiscount(1, 999_999)
        assertEquals(1000L, c.items[0].discount)
        assertEquals(0L, c.totals()!!.total)
    }

    @Test fun reducing_quantity_re_clamps_the_discount() {
        val c = cart().add(product(price = 1000)).setQty(1, 3000).setLineDiscount(1, 2500).setQty(1, 1000)
        assertEquals(1000L, c.items[0].discount)
    }

    @Test fun invoice_discount_is_clamped_to_what_is_left_after_item_discounts() {
        val c = cart().add(product(price = 1000)).setLineDiscount(1, 400).setInvoiceDiscount(9_999)
        assertEquals(600L, c.invoiceDiscount)
        assertEquals(0L, c.totals()!!.total)
    }

    @Test fun price_override_changes_the_line_but_keeps_the_list_price() {
        val c = cart().add(product(price = 1500)).setUnitPrice(1, 1200)
        assertEquals(1200L, c.items[0].unitPrice)
        assertEquals(1500L, c.items[0].listPrice)
    }

    @Test fun tax_applies_to_the_discounted_amount() {
        // 100.00 - 10.00 = 90.00; 14% = 12.60; total 102.60
        val c = Cart("c", taxBasisPoints = 1400).add(product(price = 10000)).setLineDiscount(1, 1000)
        assertEquals(1260L, c.totals()!!.taxAmount)
        assertEquals(10260L, c.totals()!!.total)
    }

    @Test fun removing_the_last_line_clears_the_invoice_discount() {
        val c = cart().add(product(price = 1000)).setInvoiceDiscount(300).remove(1)
        assertEquals(0L, c.invoiceDiscount)
    }
}
