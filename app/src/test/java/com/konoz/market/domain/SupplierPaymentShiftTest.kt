package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Supplier
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.repo.ProductInput
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner decision: money paid to suppliers leaves the cash drawer of the open shift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupplierPaymentShiftTest {

    private lateinit var db: KonozDatabase
    private lateinit var purchases: PurchaseService
    private lateinit var shifts: ShiftService
    private lateinit var repo: ProductRepository
    private val now = 1_700_000_000_000L
    private var userId = 0L
    private var supplierId = 0L
    private var productId = 0L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        purchases = PurchaseService(db) { now }
        shifts = ShiftService(db) { now }
        repo = ProductRepository(db) { now }
        runBlocking {
            userId = db.users().insert(User(name = "t", username = "t", pinHash = "x", salt = "y", role = UserRole.ADMIN, createdAt = now))
            supplierId = db.suppliers().insert(Supplier(name = "S", createdAt = now))
            productId = repo.save(ProductInput(name = "P", costPrice = 1000, salePrice = 1500, openingQtyMilli = 20_000), null)
        }
    }

    @After fun tearDown() = db.close()

    private fun draft(uuid: String, qtyMilli: Long, unitCost: Long, paid: Long, isReturn: Boolean = false) =
        PurchaseDraft(uuid, supplierId, userId, listOf(PurchaseLine(productId, qtyMilli, unitCost)), 0L, paid, isReturn = isReturn)

    @Test fun a_payment_made_at_approval_reduces_the_expected_cash_of_the_open_shift() = runBlocking {
        val shiftId = shifts.open(10_000L, userId)

        purchases.approve(draft("p1", 10_000, 800, paid = 3000)) // total 80.00, paid 30.00

        val s = shifts.summary(shiftId)
        assertEquals(3000L, s.supplierPayments)
        assertEquals(7000L, s.expectedCash) // 100.00 opening - 30.00 paid
    }

    @Test fun settling_later_also_leaves_the_drawer() = runBlocking {
        val shiftId = shifts.open(10_000L, userId)
        val inv = purchases.approve(draft("p1", 10_000, 800, paid = 0))

        purchases.settle(inv.invoiceId, 2500, userId)

        assertEquals(7500L, shifts.summary(shiftId).expectedCash)
    }

    @Test fun a_refund_collected_for_a_supplier_return_comes_back_into_the_drawer() = runBlocking {
        val shiftId = shifts.open(10_000L, userId)

        purchases.approve(draft("r1", 2000, 500, paid = 1000, isReturn = true)) // return worth 10.00, refund 10.00 in cash

        val s = shifts.summary(shiftId)
        assertEquals(-1000L, s.supplierPayments)
        assertEquals(11_000L, s.expectedCash)
    }

    @Test fun an_unpaid_purchase_does_not_touch_the_drawer() = runBlocking {
        val shiftId = shifts.open(10_000L, userId)

        purchases.approve(draft("p1", 10_000, 800, paid = 0))

        assertEquals(10_000L, shifts.summary(shiftId).expectedCash)
        assertEquals(0L, db.supplierPayments().netForShift(shiftId))
    }

    @Test fun a_retried_approval_records_the_payment_only_once() = runBlocking {
        val shiftId = shifts.open(10_000L, userId)
        val d = draft("same", 10_000, 800, paid = 3000)

        purchases.approve(d)
        purchases.approve(d)

        assertEquals(3000L, db.supplierPayments().netForShift(shiftId))
    }

    @Test fun a_payment_with_no_open_shift_is_kept_but_belongs_to_no_drawer() = runBlocking {
        val inv = purchases.approve(draft("p1", 10_000, 800, paid = 3000))

        val rows = db.supplierPayments().forInvoice(inv.invoiceId)
        assertEquals(1, rows.size)
        assertNull(rows.single().shiftId)
        assertEquals(3000L, rows.single().amount)

        // a shift opened afterwards starts clean
        val shiftId = shifts.open(5000L, userId)
        assertEquals(5000L, shifts.summary(shiftId).expectedCash)
    }

    @Test fun closing_the_shift_stores_the_expected_cash_that_includes_supplier_payments() = runBlocking {
        val shiftId = shifts.open(10_000L, userId)
        purchases.approve(draft("p1", 10_000, 800, paid = 3000))

        val closed = shifts.close(shiftId, actualCash = 7000L, userId = userId)

        assertEquals(7000L, closed.expectedCash)
        assertEquals(0L, closed.difference)
    }
}
