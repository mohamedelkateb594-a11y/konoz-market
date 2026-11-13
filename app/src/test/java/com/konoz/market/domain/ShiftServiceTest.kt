package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Expense
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.Payment
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesReturn
import com.konoz.market.data.entity.ShiftStatus
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ShiftService
    private var userId = 0L
    private var now = 1_700_000_000_000L
    private var seq = 0

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ShiftService(db, { now })
        userId = runBlocking {
            db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    /** A completed invoice of the shift with the given (already net) payments. */
    private fun sale(shiftId: Long, vararg pays: Pair<PaymentMethod, Long>): Long = runBlocking {
        val total = pays.sumOf { it.second }
        val id = db.sales().insertInvoice(
            SalesInvoice(
                uuid = "inv-${seq++}", number = seq.toLong(), userId = userId, shiftId = shiftId,
                status = InvoiceStatus.COMPLETED, total = total, paidTotal = total, createdAt = now, completedAt = now,
            )
        )
        db.sales().insertPayments(pays.map { Payment(invoiceId = id, shiftId = shiftId, method = it.first, amount = it.second, createdAt = now) })
        id
    }

    private fun refund(invoiceId: Long, shiftId: Long, amount: Long, method: PaymentMethod) = runBlocking {
        db.sales().insertReturn(
            SalesReturn(
                uuid = "ret-${seq++}", salesInvoiceId = invoiceId, userId = userId, refundAmount = amount,
                refundMethod = method, shiftId = shiftId, createdAt = now,
            )
        )
    }

    private fun expense(shiftId: Long, amount: Long, cash: Boolean) = runBlocking {
        db.expenses().insert(
            Expense(
                uuid = "exp-${seq++}", amount = amount, category = ExpenseCategory.MISC, description = "x",
                paidInCash = cash, userId = userId, shiftId = shiftId, createdAt = now,
            )
        )
    }

    @Test fun opening_creates_an_open_shift_with_the_opening_cash_and_an_audit_row() = runBlocking {
        val id = service.open(10_000, userId)

        val s = db.shifts().getById(id)!!
        assertEquals(ShiftStatus.OPEN, s.status)
        assertEquals(10_000L, s.openingCash)
        assertEquals(now, s.openedAt)
        assertEquals(userId, s.userId)
        assertEquals(id, service.getOpenShift()!!.id)
        assertEquals("SHIFT_OPENED", db.audit().observeRecent(10).first().single().action)
    }

    @Test fun a_negative_opening_cash_is_rejected_and_nothing_is_saved() {
        assertThrows(ShiftException.InvalidAmount::class.java) { runBlocking { service.open(-1, userId) } }
        assertNull(runBlocking { service.getOpenShift() })
        assertEquals(0, runBlocking { db.audit().observeRecent(10).first().size })
    }

    @Test fun only_one_shift_can_be_open_at_a_time() = runBlocking {
        val first = service.open(0, userId)

        val ex = assertThrows(ShiftException.AlreadyOpen::class.java) { runBlocking { service.open(500, userId) } }

        assertEquals(first, ex.shiftId)
        assertEquals(1, db.shifts().observeAll().first().size)
    }

    @Test fun expected_cash_is_opening_plus_cash_sales_minus_cash_refunds_and_cash_expenses() = runBlocking {
        val shift = service.open(10_000, userId)
        val inv = sale(shift, PaymentMethod.CASH to 5_000)
        sale(shift, PaymentMethod.CASH to 3_000, PaymentMethod.CARD to 2_000)
        sale(shift, PaymentMethod.WALLET to 1_000)
        expense(shift, 1_500, cash = true)
        expense(shift, 700, cash = false) // paid by other means: drawer untouched
        refund(inv, shift, 800, PaymentMethod.CASH)
        refund(inv, shift, 400, PaymentMethod.CARD) // refunded to card: drawer untouched

        val s = service.summary(shift)

        assertEquals(10_000L, s.openingCash)
        assertEquals(8_000L, s.cashSales)
        assertEquals(2_000L, s.cardSales)
        assertEquals(1_000L, s.walletSales)
        assertEquals(3_000L, s.otherSales)
        assertEquals(11_000L, s.totalSales)
        assertEquals(1_500L, s.cashExpenses)
        assertEquals(800L, s.cashRefunds)
        assertEquals(15_700L, s.expectedCash) // 10000 + 8000 - 800 - 1500
        assertNull(s.actualCash)
        assertNull(s.difference)
    }

    @Test fun other_shifts_do_not_affect_the_summary() = runBlocking {
        val first = service.open(1_000, userId)
        sale(first, PaymentMethod.CASH to 9_999)
        service.close(first, 10_999, userId)
        now += 1_000
        val second = service.open(2_000, userId)
        sale(second, PaymentMethod.CASH to 500)

        val s = service.summary(second)

        assertEquals(500L, s.cashSales)
        assertEquals(2_500L, s.expectedCash)
    }

    @Test fun closing_stores_expected_actual_and_a_negative_difference_for_a_shortage() = runBlocking {
        val shift = service.open(10_000, userId)
        sale(shift, PaymentMethod.CASH to 5_000)
        now += 60_000

        val s = service.close(shift, 14_800, userId)

        assertEquals(15_000L, s.expectedCash)
        assertEquals(14_800L, s.actualCash)
        assertEquals(-200L, s.difference)
        val saved = db.shifts().getById(shift)!!
        assertEquals(ShiftStatus.CLOSED, saved.status)
        assertEquals(now, saved.closedAt)
        assertEquals(15_000L, saved.expectedCash)
        assertEquals(14_800L, saved.actualCash)
        assertEquals(-200L, saved.difference)
        assertNull(service.getOpenShift())
        assertEquals("SHIFT_CLOSED", db.audit().observeRecent(10).first().first().action)
    }

    @Test fun a_surplus_gives_a_positive_difference_and_an_exact_count_gives_zero() = runBlocking {
        val a = service.open(1_000, userId)
        assertEquals(300L, service.close(a, 1_300, userId).difference)
        val b = service.open(1_000, userId)
        assertEquals(0L, service.close(b, 1_000, userId).difference)
    }

    @Test fun a_closed_shift_cannot_be_closed_again_and_the_first_count_is_kept() = runBlocking {
        val shift = service.open(1_000, userId)
        service.close(shift, 900, userId)

        assertThrows(ShiftException.AlreadyClosed::class.java) { runBlocking { service.close(shift, 5_000, userId) } }

        assertEquals(900L, db.shifts().getById(shift)!!.actualCash)
        assertEquals(1, db.audit().observeRecent(10).first().count { it.action == "SHIFT_CLOSED" })
    }

    @Test fun closing_validates_amount_and_existence() = runBlocking {
        val shift = service.open(1_000, userId)

        assertThrows(ShiftException.InvalidAmount::class.java) { runBlocking { service.close(shift, -1, userId) } }
        assertThrows(ShiftException.NotFound::class.java) { runBlocking { service.close(999, 0, userId) } }
        assertThrows(ShiftException.NotFound::class.java) { runBlocking { service.summary(999) } }
        assertEquals(ShiftStatus.OPEN, db.shifts().getById(shift)!!.status)
    }

    @Test fun a_new_shift_can_be_opened_after_the_previous_one_is_closed() = runBlocking {
        val first = service.open(1_000, userId)
        service.close(first, 1_000, userId)

        val second = service.open(2_000, userId)

        assertEquals(second, service.getOpenShift()!!.id)
        assertEquals(2, db.shifts().observeAll().first().size)
    }

    @Test fun the_summary_of_a_closed_shift_shows_the_saved_count() = runBlocking {
        val shift = service.open(1_000, userId)
        sale(shift, PaymentMethod.CASH to 500)
        service.close(shift, 1_450, userId)

        val s = service.summary(shift)

        assertEquals(1_500L, s.expectedCash)
        assertEquals(1_450L, s.actualCash)
        assertEquals(-50L, s.difference)
    }
}
