package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.ReturnCondition
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.repo.ProductInput
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReturnServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ReturnService
    private lateinit var sales: SaleService
    private val now = 1_700_000_000_000L
    private var userId = 0L
    private var productId = 0L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ReturnService(db) { now }
        sales = SaleService(db) { now }
        runBlocking {
            userId = db.users().insert(User(name = "t", username = "t", pinHash = "x", salt = "y", role = UserRole.ADMIN, createdAt = now))
            productId = ProductRepository(db) { now }.save(
                ProductInput(name = "P", costPrice = 1000, salePrice = 1500, openingQtyMilli = 10_000), null,
            )
        }
    }

    @After fun tearDown() = db.close()

    private fun sell(uuid: String, qtyMilli: Long, price: Long = 1500, invoiceDiscount: Long = 0, tax: Int = 0): SaleResult = runBlocking {
        sales.completeSale(
            SaleDraft(uuid, userId, null, listOf(CartLine(productId, qtyMilli, price)), invoiceDiscount, tax),
            listOf(PaymentInput(PaymentMethod.CASH, 1_000_000)),
        )
    }

    private fun itemId(invoiceId: Long): Long = runBlocking { db.sales().getItems(invoiceId).single().id }

    private fun ret(uuid: String, invoiceId: Long, itemId: Long, qty: Long, condition: ReturnCondition = ReturnCondition.RESELLABLE) =
        ReturnDraft(uuid, invoiceId, userId, listOf(ReturnLine(itemId, qty, condition)), "reason")

    private fun stockQty() = runBlocking { db.products().getById(productId)!!.currentQtyMilli }

    @Test fun partial_returns_refund_exactly_the_paid_share_and_restock() = runBlocking {
        val s = sell("s1", 3000, 1500, invoiceDiscount = 450) // 45.00 - 4.50 = 40.50
        val item = itemId(s.invoiceId)
        assertEquals(4050L, s.total)
        assertEquals(7000L, stockQty())

        val r1 = service.createReturn(ret("r1", s.invoiceId, item, 1000))
        val r2 = service.createReturn(ret("r2", s.invoiceId, item, 2000))

        assertEquals(1350L, r1.refundAmount)
        assertEquals(2700L, r2.refundAmount) // 4050 - 1350: never a piastre more than was paid
        assertEquals(10_000L, stockQty())
        assertEquals(10_000L, db.stock().movementTotal(productId))
        assertEquals(10_000L, db.stock().batchTotal(productId))

        assertThrows(ReturnException.QuantityExceeds::class.java) {
            runBlocking { service.createReturn(ret("r3", s.invoiceId, item, 1000)) }
        }
    }

    @Test fun tax_is_refunded_proportionally_and_adds_up_to_the_invoice_total() = runBlocking {
        val s = sell("s2", 3000, 1000, tax = 1400) // 30.00 + 14% = 34.20
        val item = itemId(s.invoiceId)
        assertEquals(3420L, s.total)
        val r1 = service.createReturn(ret("r1", s.invoiceId, item, 1000))
        val r2 = service.createReturn(ret("r2", s.invoiceId, item, 2000))
        assertEquals(1140L, r1.refundAmount)
        assertEquals(3420L, r1.refundAmount + r2.refundAmount)
    }

    @Test fun asking_for_more_than_was_sold_is_rejected_and_nothing_is_saved() {
        val s = sell("s3", 3000)
        val item = itemId(s.invoiceId)
        assertThrows(ReturnException.QuantityExceeds::class.java) {
            runBlocking { service.createReturn(ret("x", s.invoiceId, item, 4000)) }
        }
        runBlocking {
            assertNull(db.sales().getReturnByUuid("x"))
            assertEquals(7000L, stockQty())
        }
    }

    @Test fun a_damaged_return_refunds_but_does_not_restock_and_stays_visible_in_the_ledger() = runBlocking {
        val s = sell("s4", 1000)
        val item = itemId(s.invoiceId)
        assertEquals(9000L, stockQty())

        val r = service.createReturn(ret("r1", s.invoiceId, item, 1000, ReturnCondition.DAMAGED))

        assertEquals(1500L, r.refundAmount)
        assertEquals(9000L, stockQty())
        assertEquals(9000L, db.stock().movementTotal(productId))
        val types = db.stock().observeMovements(productId).first().map { it.type }
        assertTrue(MovementType.RETURN_IN in types)
        assertTrue(MovementType.DAMAGED in types)
    }

    @Test fun retrying_the_same_uuid_refunds_and_restocks_once() = runBlocking {
        val s = sell("s5", 1000)
        val item = itemId(s.invoiceId)
        val d = ret("same", s.invoiceId, item, 1000)

        val first = service.createReturn(d)
        val second = service.createReturn(d)

        assertTrue(second.duplicate)
        assertEquals(first.returnId, second.returnId)
        assertEquals(10_000L, stockQty())
        assertEquals(1, db.sales().observeReturns().first().size)
    }

    @Test fun a_held_invoice_cannot_be_returned() {
        val heldId = runBlocking { sales.holdInvoice(SaleDraft("h", userId, null, listOf(CartLine(productId, 1000, 1500)))) }
        assertThrows(ReturnException.InvoiceNotCompleted::class.java) {
            runBlocking { service.createReturn(ret("x", heldId, 1, 1000)) }
        }
    }

    @Test fun an_item_from_another_invoice_is_rejected() {
        val s = sell("s6", 1000)
        assertThrows(ReturnException.ItemNotInInvoice::class.java) {
            runBlocking { service.createReturn(ret("x", s.invoiceId, 9999, 1000)) }
        }
    }

    @Test fun fractional_quantities_are_rejected_for_piece_products() {
        val s = sell("s7", 3000)
        val item = itemId(s.invoiceId)
        assertThrows(ReturnException.InvalidQuantity::class.java) {
            runBlocking { service.createReturn(ret("x", s.invoiceId, item, 1500)) }
        }
    }

    @Test fun the_return_screen_lookup_shows_what_is_still_returnable() = runBlocking {
        val s = sell("s8", 3000)
        val item = itemId(s.invoiceId)
        assertEquals(3000L, service.loadForReturn(s.number)!!.lines.single().remainingMilli)
        service.createReturn(ret("r1", s.invoiceId, item, 1000))
        assertEquals(2000L, service.loadForReturn(s.number)!!.lines.single().remainingMilli)
        assertNull(service.loadForReturn(999))
    }

    @Test fun resellable_goods_come_back_as_a_batch_with_the_cost_of_the_sale() = runBlocking {
        val s = sell("s9", 3000)
        val item = itemId(s.invoiceId)
        service.createReturn(ret("r1", s.invoiceId, item, 1000))
        val batches = db.stock().observeBatches(productId).first()
        assertEquals(2, batches.size) // the opening batch and the returned one
        assertTrue(batches.any { it.qtyMilli == 1000L && it.unitCost == 1000L })
    }
}
