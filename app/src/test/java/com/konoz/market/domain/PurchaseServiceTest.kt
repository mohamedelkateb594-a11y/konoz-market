package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.PurchaseStatus
import com.konoz.market.data.entity.Supplier
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.repo.ProductInput
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PurchaseServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: PurchaseService
    private lateinit var repo: ProductRepository
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private var userId = 0L
    private var supplierId = 0L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = PurchaseService(db) { now }
        repo = ProductRepository(db) { now }
        runBlocking {
            userId = db.users().insert(User(name = "t", username = "t", pinHash = "x", salt = "y", role = UserRole.ADMIN, createdAt = now))
            supplierId = db.suppliers().insert(Supplier(name = "S", createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    private fun addProduct(name: String = "P"): Long = runBlocking {
        repo.save(ProductInput(name = name, costPrice = 1000, salePrice = 1500), null)
    }

    private fun draft(
        uuid: String,
        vararg lines: PurchaseLine,
        paid: Long = 0,
        discount: Long = 0,
        isReturn: Boolean = false,
    ) = PurchaseDraft(uuid, supplierId, userId, lines.toList(), discount, paid, isReturn = isReturn)

    @Test fun approving_a_purchase_adds_a_batch_a_movement_and_stock_and_updates_the_cost() = runBlocking {
        val p = addProduct()
        val r = service.approve(draft("u1", PurchaseLine(p, 10_000, 800, expiryDate = now + 30 * day), paid = 4000))

        assertEquals(8000L, r.total)
        assertEquals(4000L, r.paid)
        assertEquals(4000L, r.remaining)
        assertEquals(1L, r.number)
        assertFalse(r.duplicate)

        val batch = db.stock().observeBatches(p).first().single()
        assertEquals(10_000L, batch.qtyMilli)
        assertEquals(800L, batch.unitCost)
        assertEquals(now + 30 * day, batch.expiryDate)

        val product = db.products().getById(p)!!
        assertEquals(10_000L, product.currentQtyMilli)
        assertEquals(800L, product.costPrice) // cost price = latest purchase cost
        assertEquals(10_000L, db.stock().movementTotal(p))
        assertEquals(10_000L, db.stock().batchTotal(p))
        assertEquals(PurchaseStatus.APPROVED, db.purchases().getInvoice(r.invoiceId)!!.status)
    }

    @Test fun approving_the_same_uuid_twice_posts_nothing_twice() = runBlocking {
        val p = addProduct()
        val d = draft("same", PurchaseLine(p, 5000, 1000))
        val first = service.approve(d)
        val second = service.approve(d)

        assertTrue(second.duplicate)
        assertEquals(first.invoiceId, second.invoiceId)
        assertEquals(first.number, second.number)
        assertEquals(5000L, db.products().getById(p)!!.currentQtyMilli)
        assertEquals(1, db.stock().observeBatches(p).first().size)
    }

    @Test fun a_draft_does_not_touch_stock_and_approving_it_reuses_the_same_row() = runBlocking {
        val p = addProduct()
        val d = draft("dr", PurchaseLine(p, 5000, 1000))
        val draftId = service.saveDraft(d)

        val saved = db.purchases().getInvoice(draftId)!!
        assertEquals(PurchaseStatus.DRAFT, saved.status)
        assertNull(saved.number)
        assertEquals(0L, db.products().getById(p)!!.currentQtyMilli)
        assertEquals(d.lines, service.loadDraft(draftId)!!.lines)

        val r = service.approve(d)
        assertEquals(draftId, r.invoiceId)
        assertEquals(1L, r.number)
        assertEquals(5000L, db.products().getById(p)!!.currentQtyMilli)
        assertNull(service.loadDraft(draftId)) // no longer a draft
    }

    @Test fun the_invoice_discount_is_included_in_batch_costs() = runBlocking {
        val a = addProduct("A")
        val b = addProduct("B")
        // two lines of 10.00 each, invoice discount 2.00 -> total 18.00, each line effectively 9.00 -> 0.90 per piece
        val r = service.approve(
            draft("u", PurchaseLine(a, 10_000, 1000), PurchaseLine(b, 10_000, 1000), discount = 2000)
        )
        assertEquals(18_000L, r.total)
        assertEquals(900L, db.stock().observeBatches(a).first().single().unitCost)
        assertEquals(900L, db.stock().observeBatches(b).first().single().unitCost)
    }

    @Test fun a_return_takes_stock_out_fefo_earliest_expiry_first() = runBlocking {
        val p = addProduct()
        service.approve(draft("p1", PurchaseLine(p, 5000, 800, expiryDate = now + 10 * day)))
        service.approve(draft("p2", PurchaseLine(p, 5000, 900, expiryDate = now + 60 * day)))

        val r = service.approve(draft("r1", PurchaseLine(p, 7000, 850), isReturn = true))

        assertTrue(r.isReturn)
        assertEquals(5950L, r.total) // 7.000 x 8.50
        val left = db.stock().fefoBatches(p).single() // only the later batch still has stock
        assertEquals(3000L, left.qtyMilli)
        assertEquals(900L, left.unitCost)
        assertEquals(3000L, db.products().getById(p)!!.currentQtyMilli)
        assertEquals(3000L, db.stock().movementTotal(p))
        val returned = db.stock().observeMovements(p).first().filter { it.type == MovementType.PURCHASE_RETURN }
        assertEquals(-7000L, returned.sumOf { it.qtyMilli })
    }

    @Test fun returning_more_than_the_stock_is_rejected_and_nothing_is_saved() {
        val p = addProduct()
        runBlocking { service.approve(draft("p1", PurchaseLine(p, 2000, 800))) }

        assertThrows(PurchaseException.InsufficientStockForReturn::class.java) {
            runBlocking { service.approve(draft("r", PurchaseLine(p, 3000, 800), isReturn = true)) }
        }
        runBlocking {
            assertEquals(2000L, db.products().getById(p)!!.currentQtyMilli)
            assertNull(db.purchases().getByUuid("r"))
        }
    }

    @Test fun paying_more_than_the_total_is_rejected() {
        val p = addProduct()
        assertThrows(PurchaseException.InvalidAmount::class.java) {
            runBlocking { service.approve(draft("x", PurchaseLine(p, 1000, 1000), paid = 1001)) }
        }
        runBlocking { assertNull(db.purchases().getByUuid("x")) }
    }

    @Test fun settlements_reduce_the_unsettled_amount_and_returns_reduce_the_supplier_balance() = runBlocking {
        val p = addProduct()
        val purchase = service.approve(draft("p1", PurchaseLine(p, 10_000, 800), paid = 4000)) // total 80.00, 40.00 unpaid
        assertEquals(4000L, service.supplierBalance(supplierId))

        val after = service.settle(purchase.invoiceId, 1000, userId)
        assertEquals(3000L, after.remaining)
        assertEquals(3000L, service.supplierBalance(supplierId))

        assertThrows(PurchaseException.InvalidAmount::class.java) {
            runBlocking { service.settle(purchase.invoiceId, 3001, userId) }
        }

        // return worth 5.00 with no refund collected: we now owe 30.00 - 5.00
        service.approve(draft("r1", PurchaseLine(p, 1000, 500), isReturn = true))
        assertEquals(2500L, service.supplierBalance(supplierId))

        val statement = service.statement(supplierId)
        assertEquals(listOf(3000L, -500L), statement.map { it.delta })
        assertEquals(2500L, statement.last().balanceAfter)
    }
}
