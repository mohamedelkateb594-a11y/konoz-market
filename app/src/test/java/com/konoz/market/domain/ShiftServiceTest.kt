package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ShiftService
    private lateinit var expenses: ExpenseService
    private var userId = 0L
    private var invoiceNumber = 0L
    private val now = 1_700_000_000_000L

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ShiftService(db, { now })
        expenses = ExpenseService(db, { now }, TimeZone.getTimeZone("UTC"))
        userId = db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
    }

    @After fun tearDown() = db.close()

    /** A completed sale paid with the given (net) amounts, attached to [shiftId]. */
    private suspend fun sale(shiftId: Long?, cash: Long = 0, card: Long = 0, wallet: Long = 0) {
        invoiceNumber++
        val id = db.sales().insertInvoice(
            SalesInvoice(
                uuid = "inv-$invoiceNumber", number = invoiceNumber, userId = userId, shiftId = shiftId, status = InvoiceStatus.COMPLETED,
                total = cash + card + wallet, createdAt = now, completedAt = now,
            )
        )
        db.sales().insertPayments(
            listOf(PaymentMethod.CASH to cash, PaymentMethod.CARD to card, PaymentMethod.WALLET to wallet)
                .filter { it.second > 0 }
                .map { (m, a) -> Payment(invoiceId = id, shiftId = shiftId, method = m, amount = a, createdAt = now) }
        )
    }

    private suspend fun refund(shiftId: Long?, amount: Long, method: PaymentMethod = PaymentMethod.CASH) {
        invoiceNumber++
        val inv = db.sales().insertInvoice(
            SalesInvoice(uuid = "inv-$invoiceNumber", number = invoiceNumber, userId = userId, status = InvoiceStatus.COMPLETED, createdAt = now, completedAt = now)
        )
        db.sales().insertReturn(
            SalesReturn(uuid = "ret-$invoiceNumber", salesInvoiceId = inv, userId = userId, refundAmount = amount, refundMethod = method, shiftId = shiftId, createdAt = now)
        )
    }

    @Test fun expected_cash_is_opening_plus_cash_sales_minus_cash_refunds_and_cash_expenses() = runBlocking {
        val shift = service.open(10_000, userId)
        sale(shift, cash = 5_000)
        sale(shift, card = 3_000, wallet = 1_000)
        refund(shift, 1_500)
        refund(shift, 900, PaymentMethod.CARD) // not cash: the drawer is untouched
        expenses.add(ExpenseInput(2_000, ExpenseCategory.MISC, "cash expense"), userId)
        expenses.add(ExpenseInput(700, ExpenseCategory.MISC, "card expense", paidInCash = false), userId)

        val r = service.currentReport()!!

        assertEquals(10_000L, r.shift.openingCash)
        assertEquals(5_000L, r.cashSales)
        assertEquals(3_000L, r.cardSales)
        assertEquals(1_000L, r.walletSales)
        assertEquals(4_000L, r.otherPayments)
        assertEquals(1_500L, r.cashRefunds)
        assertEquals(2_000L, r.cashExpenses)
        assertEquals(11_500L, r.expectedCash) // 10000 + 5000 - 1500 - 2000
        assertNull(r.actualCash)
    }

    @Test fun sales_of_other_shifts_or_without_a_shift_do_not_count() = runBlocking {
        sale(null, cash = 9_999) // before any shift was opened
        val shift = service.open(1_000, userId)
        sale(shift, cash = 500)

        assertEquals(1_500L, service.currentReport()!!.expectedCash)
    }

    @Test fun closing_fixes_expected_actual_and_difference_and_audits() = runBlocking {
        val shift = service.open(10_000, userId)
        sale(shift, cash = 5_000)

        val report = service.close(actualCash = 14_900, userId = userId)

        assertEquals(15_000L, report.expectedCash)
        assertEquals(14_900L, report.actualCash)
        assertEquals(-100L, report.difference) // shortage
        assertEquals(ShiftStatus.CLOSED, report.shift.status)
        assertEquals(now, report.shift.closedAt)
        assertNull(service.currentReport())
        assertEquals(listOf("SHIFT_CLOSED", "SHIFT_OPENED"), db.audit().observeRecent(10).first().map { it.action })
    }

    @Test fun a_closed_shift_report_does_not_change_when_later_sales_arrive() = runBlocking {
        val shift = service.open(1_000, userId)
        sale(shift, cash = 500)
        service.close(actualCash = 1_500, userId = userId)

        sale(shift, cash = 700) // late row attached to the closed shift

        val r = service.report(shift)
        assertEquals(1_500L, r.expectedCash)
        assertEquals(0L, r.difference)
    }

    @Test fun overage_is_a_positive_difference() = runBlocking {
        service.open(1_000, userId)

        assertEquals(250L, service.close(actualCash = 1_250, userId = userId).difference)
    }

    @Test fun only_one_shift_can_be_open_and_a_new_one_can_follow_a_closed_one() = runBlocking {
        service.open(1_000, userId)
        assertThrows(ShiftException.AlreadyOpen::class.java) { runBlocking { service.open(2_000, userId) } }

        service.close(actualCash = 1_000, userId = userId)
        val second = service.open(2_000, userId)

        assertEquals(second, service.currentReport()!!.shift.id)
        assertEquals(2, db.shifts().observeAll().first().size)
    }

    @Test fun invalid_amounts_and_missing_shift_are_rejected() {
        assertThrows(ShiftException.InvalidAmount::class.java) { runBlocking { service.open(-1, userId) } }
        assertThrows(ShiftException.NoOpenShift::class.java) { runBlocking { service.close(100, userId) } }
        runBlocking { service.open(0, userId) } // an empty drawer is a valid opening
        assertThrows(ShiftException.InvalidAmount::class.java) { runBlocking { service.close(-5, userId) } }
        assertTrue(runBlocking { service.currentReport() } != null) // the failed close left the shift open
        assertThrows(ShiftException.NotFound::class.java) { runBlocking { service.report(999) } }
    }

    @Test fun expense_is_linked_to_the_open_shift() = runBlocking {
        val shift = service.open(5_000, userId)

        val id = expenses.add(ExpenseInput(1_000, ExpenseCategory.WATER, "water"), userId)

        assertEquals(shift, db.expenses().getById(id)!!.shiftId)
    }
}
