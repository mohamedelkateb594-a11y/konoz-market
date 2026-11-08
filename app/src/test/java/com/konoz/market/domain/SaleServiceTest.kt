package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.StockBatch
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class SaleServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: SaleService
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private var userId = 0L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = SaleService(db) { now }
        userId = runBlocking {
            db.users().insert(User(name = "t", username = "t", pinHash = "x", salt = "y", role = UserRole.ADMIN, createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    // ---------- helpers ----------
    private fun addProduct(cost: Long = 1000, price: Long = 1500): Long = runBlocking {
        db.products().insert(Product(name = "p", costPrice = cost, salePrice = price, createdAt = now))
    }

    private fun addBatch(productId: Long, qtyMilli: Long, cost: Long, expiry: Long?): Long = runBlocking {
        val id = db.stock().insertBatch(StockBatch(productId = productId, qtyMilli = qtyMilli, unitCost = cost, expiryDate = expiry, receivedAt = now))
        db.stock().insertMovement(
            InventoryMovement(productId = productId, batchId = id, type = MovementType.PURCHASE, qtyMilli = qtyMilli,
                unitCost = cost, idempotencyKey = "T:$id", createdAt = now)
        )
        db.products().adjustQty(productId, qtyMilli)
        id
    }

    private fun draft(uuid: String, productId: Long, qtyMilli: Long, price: Long = 1500) = SaleDraft(
        uuid = uuid, userId = userId, lines = listOf(CartLine(productId, qtyMilli, price)),
    )

    private fun cash(amount: Long) = listOf(PaymentInput(PaymentMethod.CASH, amount))

    // ---------- tests ----------
    @Test fun fefo_sells_earliest_expiry_first_and_keeps_ledger_consistent() = runBlocking {
        val p = addProduct()
        val late = addBatch(p, 5000, 1200, now + 60 * day)
        val soon = addBatch(p, 2000, 1000, now + 10 * day)

        val r = service.completeSale(draft("u1", p, 4000), cash(6000))

        val batches = db.stock().observeBatches(p).first().associateBy { it.id }
        assertEquals(0L, batches.getValue(soon).qtyMilli)
        assertEquals(3000L, batches.getValue(late).qtyMilli)
        assertEquals(3000L, db.products().getById(p)!!.currentQtyMilli)
        assertEquals(3000L, db.stock().movementTotal(p))
        assertEquals(3000L, db.stock().batchTotal(p))
        // cost: 2.000 x 10.00 + 2.000 x 12.00 = 44.00
        assertEquals(4400L, r.totalCost)
    }

    @Test fun cost_is_snapshotted_and_not_changed_by_later_cost_edits() = runBlocking {
        val p = addProduct()
        addBatch(p, 5000, 1200, now + 60 * day)
        addBatch(p, 2000, 1000, now + 10 * day)
        val r = service.completeSale(draft("u1", p, 4000), cash(6000))

        db.products().setCostPrice(p, 9999)

        assertEquals(4400L, db.sales().getInvoice(r.invoiceId)!!.totalCost)
    }

    @Test fun mixed_payment_stores_net_cash_and_computes_change() = runBlocking {
        val p = addProduct(price = 1000)
        addBatch(p, 20000, 500, null)
        val d = draft("u1", p, 10000, price = 1000) // total 100.00

        val r = service.completeSale(
            d, listOf(PaymentInput(PaymentMethod.CARD, 6000), PaymentInput(PaymentMethod.CASH, 5000)),
        )

        assertEquals(10000L, r.total)
        assertEquals(11000L, r.paidTotal)
        assertEquals(1000L, r.changeDue)
        val stored = db.sales().getPayments(r.invoiceId)
        assertEquals(10000L, stored.sumOf { it.amount })
        assertEquals(4000L, stored.first { it.method == PaymentMethod.CASH }.amount)
        assertEquals(6000L, stored.first { it.method == PaymentMethod.CARD }.amount)
    }

    @Test fun retrying_the_same_uuid_posts_nothing_twice() = runBlocking {
        val p = addProduct()
        addBatch(p, 5000, 1000, null)
        val d = draft("same", p, 2000)

        val first = service.completeSale(d, cash(3000))
        val second = service.completeSale(d, cash(3000))

        assertEquals(false, first.duplicate)
        assertTrue(second.duplicate)
        assertEquals(first.invoiceId, second.invoiceId)
        assertEquals(first.number, second.number)
        assertEquals(3000L, db.products().getById(p)!!.currentQtyMilli)
        assertEquals(3000L, db.stock().movementTotal(p))
    }

    @Test fun invoice_numbers_are_sequential() = runBlocking {
        val p = addProduct()
        addBatch(p, 9000, 1000, null)
        val a = service.completeSale(draft("a", p, 1000), cash(1500))
        val b = service.completeSale(draft("b", p, 1000), cash(1500))
        assertEquals(1L, a.number)
        assertEquals(2L, b.number)
    }

    @Test fun hold_does_not_touch_stock_or_consume_a_number_and_can_be_resumed_and_completed() = runBlocking {
        val p = addProduct()
        addBatch(p, 5000, 1000, null)
        val d = draft("held", p, 2000)

        val heldId = service.holdInvoice(d)
        val held = db.sales().getInvoice(heldId)!!
        assertEquals(InvoiceStatus.HELD, held.status)
        assertNull(held.number)
        assertEquals(5000L, db.products().getById(p)!!.currentQtyMilli)

        val resumed = service.resumeInvoice(heldId)
        assertNotNull(resumed)
        assertEquals(d.lines, resumed!!.lines)

        val r = service.completeSale(resumed, cash(3000))
        assertEquals(heldId, r.invoiceId)
        assertEquals(1L, r.number)
        assertEquals(3000L, db.products().getById(p)!!.currentQtyMilli)
        assertNull(service.resumeInvoice(heldId)) // no longer held
    }

    @Test fun insufficient_payment_is_rejected_and_nothing_is_saved() {
        val p = addProduct()
        addBatch(p, 5000, 1000, null)
        assertThrows(SaleException.InsufficientPayment::class.java) {
            runBlocking { service.completeSale(draft("x", p, 2000), cash(2999)) }
        }
        runBlocking {
            assertNull(db.sales().getByUuid("x"))
            assertEquals(5000L, db.products().getById(p)!!.currentQtyMilli)
        }
    }

    @Test fun card_overpayment_is_rejected() {
        val p = addProduct()
        addBatch(p, 5000, 1000, null)
        assertThrows(SaleException.NonCashOverpayment::class.java) {
            runBlocking {
                service.completeSale(draft("x", p, 1000), listOf(PaymentInput(PaymentMethod.CARD, 2000)))
            }
        }
    }

    @Test fun expired_stock_requires_explicit_override() {
        val p = addProduct()
        addBatch(p, 5000, 1000, now - day)
        assertThrows(SaleException.ExpiredProduct::class.java) {
            runBlocking { service.completeSale(draft("e1", p, 1000), cash(1500)) }
        }
        val r = runBlocking {
            service.completeSale(draft("e2", p, 1000), cash(1500), SaleOptions(allowExpired = true))
        }
        assertEquals(1L, r.number)
    }

    @Test fun negative_stock_follows_the_option() {
        val p = addProduct()
        assertThrows(SaleException.InsufficientStock::class.java) {
            runBlocking { service.completeSale(draft("n1", p, 1000), cash(1500), SaleOptions(allowNegativeStock = false)) }
        }
        runBlocking {
            service.completeSale(draft("n2", p, 1000), cash(1500))
            assertEquals(-1000L, db.products().getById(p)!!.currentQtyMilli)
        }
    }

    @Test fun piece_products_reject_fractional_quantities() {
        val p = addProduct()
        addBatch(p, 5000, 1000, null)
        assertThrows(SaleException.InvalidQuantity::class.java) {
            runBlocking { service.completeSale(draft("f", p, 1500), cash(5000)) }
        }
    }
}
