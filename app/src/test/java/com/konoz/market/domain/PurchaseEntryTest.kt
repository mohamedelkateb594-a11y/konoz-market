package com.konoz.market.domain

import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.PurchaseInvoice
import com.konoz.market.data.entity.PurchaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseEntryTest {

    private val milk = Product(id = 1, name = "Milk", costPrice = 800, createdAt = 0)

    @Test fun adding_a_product_uses_its_cost_price_and_adding_it_again_increases_the_quantity() {
        val e = PurchaseEntry().addProduct(milk)
        assertEquals("1", e.lines[0].qty)
        assertEquals("8.00", e.lines[0].cost)
        val twice = e.addProduct(milk)
        assertEquals(1, twice.lines.size)
        assertEquals("2", twice.lines[0].qty)
    }

    @Test fun a_carton_barcode_adds_units_per_scan() {
        val e = PurchaseEntry().addProduct(milk, units = 12)
        assertEquals("12", e.lines[0].qty)
        assertEquals("24", e.addProduct(milk, units = 12).lines[0].qty)
    }

    @Test fun totals_follow_what_was_typed() {
        val e = PurchaseEntry().addProduct(milk).let {
            it.copy(lines = listOf(it.lines[0].copy(qty = "10", cost = "8", discount = "5")), invoiceDiscount = "3")
        }
        // 10 x 8.00 = 80.00 - 5.00 line discount = 75.00 - 3.00 invoice discount = 72.00
        assertEquals(7200L, e.totals()!!.total)
    }

    @Test fun missing_or_malformed_numbers_make_the_entry_invalid() {
        val base = PurchaseEntry().addProduct(milk)
        assertNull(base.copy(lines = listOf(base.lines[0].copy(cost = ""))).totals()) // cost is required
        assertNull(base.copy(lines = listOf(base.lines[0].copy(qty = "abc"))).totals())
        assertNull(base.copy(lines = listOf(base.lines[0].copy(qty = "0"))).totals())
        assertNull(base.copy(paid = "x").totals())
        assertNull(base.copy(lines = listOf(base.lines[0].copy(discount = "99"))).totals()) // discount > line total
        assertNull(PurchaseEntry().totals()) // empty
    }

    @Test fun the_draft_carries_discounts_paid_notes_and_end_of_day_expiry() {
        val day = 86_400_000L
        val e = PurchaseEntry().addProduct(milk).let {
            it.copy(
                lines = listOf(it.lines[0].copy(qty = "2", expiryUtcMillis = 10 * day, batchNo = " B1 ")),
                paid = "4", notes = " note ",
            )
        }
        val d = e.toDraft("u", supplierId = 7, userId = 3, isReturn = false)
        assertNotNull(d)
        assertEquals(7L, d!!.supplierId)
        assertEquals(400L, d.paid)
        assertEquals("note", d.notes)
        assertEquals(2000L, d.lines[0].qtyMilli)
        assertEquals(10 * day + 86_399_999L, d.lines[0].expiryDate)
        assertEquals("B1", d.lines[0].batchNo)
    }
}

class SupplierBalancesTest {
    private fun inv(id: Long, supplier: Long, remaining: Long, isReturn: Boolean = false, status: PurchaseStatus = PurchaseStatus.APPROVED) =
        PurchaseInvoice(id = id, uuid = "u$id", supplierId = supplier, userId = 1, status = status, isReturn = isReturn, remaining = remaining, createdAt = 0)

    @Test fun balances_are_per_supplier_returns_subtract_and_drafts_are_ignored() {
        val balances = SupplierBalances.of(
            listOf(
                inv(1, 1, 4000),
                inv(2, 1, 500, isReturn = true),
                inv(3, 2, 700),
                inv(4, 2, 9999, status = PurchaseStatus.DRAFT),
            )
        )
        assertEquals(3500L, balances[1L])
        assertEquals(700L, balances[2L])
    }

    @Test fun a_supplier_with_no_approved_invoices_is_absent() {
        assertNull(SupplierBalances.of(listOf(inv(1, 1, 100, status = PurchaseStatus.DRAFT)))[1L])
    }
}
