package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.data.entity.Shift
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
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpenseServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ExpenseService
    private var userId = 0L
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val day = 86_400_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ExpenseService(db, { now }, TimeZone.getTimeZone("UTC"))
        userId = runBlocking {
            db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    private fun input(amount: Long = 10_000, cash: Boolean = true, at: Long? = null) =
        ExpenseInput(amount, ExpenseCategory.ELECTRICITY, "  Electricity bill ", "  ", null, cash, at)

    private fun openShift(): Long = runBlocking { db.shifts().insert(Shift(userId = userId, openedAt = now, openingCash = 0)) }

    @Test fun adding_an_expense_stores_trimmed_text_and_writes_an_audit_row() = runBlocking {
        val id = service.add(input(), userId)

        val e = db.expenses().getById(id)!!
        assertEquals(10_000L, e.amount)
        assertEquals("Electricity bill", e.description)
        assertNull(e.notes) // blank notes are dropped
        assertEquals(userId, e.userId)
        assertEquals(now, e.createdAt)
        val log = db.audit().observeRecent(10).first().single()
        assertEquals("EXPENSE_ADDED", log.action)
        assertEquals(id, log.entityId)
    }

    @Test fun an_expense_is_linked_to_the_open_shift_only_when_there_is_one() = runBlocking {
        val without = service.add(input(), userId)
        assertNull(db.expenses().getById(without)!!.shiftId)

        val shift = openShift()
        val with = service.add(input(), userId)
        assertEquals(shift, db.expenses().getById(with)!!.shiftId)
    }

    @Test fun only_cash_expenses_count_towards_the_shift_cash() = runBlocking {
        val shift = openShift()
        service.add(input(amount = 10_000, cash = true), userId)
        service.add(input(amount = 5_000, cash = false), userId)

        assertEquals(10_000L, db.expenses().cashExpensesForShift(shift))
    }

    @Test fun invalid_amount_or_missing_description_is_rejected_and_nothing_is_saved() {
        assertThrows(ExpenseException.InvalidAmount::class.java) { runBlocking { service.add(input(amount = 0), userId) } }
        assertThrows(ExpenseException.InvalidAmount::class.java) { runBlocking { service.add(input(amount = -5), userId) } }
        assertThrows(ExpenseException.DescriptionRequired::class.java) {
            runBlocking { service.add(input().copy(description = "   "), userId) }
        }
        assertEquals(0, runBlocking { db.expenses().observeBetween(0, Long.MAX_VALUE).first().size })
        assertEquals(0, runBlocking { db.audit().observeRecent(10).first().size })
    }

    @Test fun updating_keeps_user_shift_and_time_and_records_the_change() = runBlocking {
        val shift = openShift()
        val id = service.add(input(amount = 10_000), userId)

        service.update(id, input(amount = 12_500).copy(category = ExpenseCategory.WATER, description = "Water"), userId)

        val e = db.expenses().getById(id)!!
        assertEquals(12_500L, e.amount)
        assertEquals(ExpenseCategory.WATER, e.category)
        assertEquals("Water", e.description)
        assertEquals(shift, e.shiftId)
        assertEquals(now, e.createdAt)
        assertEquals("EXPENSE_UPDATED", db.audit().observeRecent(10).first().first().action)
    }

    @Test fun deleting_removes_the_expense_returns_the_receipt_path_and_is_audited() = runBlocking {
        val id = service.add(input().copy(receiptPath = "/files/receipt1.jpg"), userId)

        val receipt = service.delete(id, userId)

        assertEquals("/files/receipt1.jpg", receipt)
        assertNull(db.expenses().getById(id))
        assertEquals("EXPENSE_DELETED", db.audit().observeRecent(10).first().first().action)
    }

    @Test fun updating_or_deleting_an_unknown_expense_fails() {
        assertThrows(ExpenseException.NotFound::class.java) { runBlocking { service.update(99, input(), userId) } }
        assertThrows(ExpenseException.NotFound::class.java) { runBlocking { service.delete(99, userId) } }
    }

    @Test fun daily_monthly_and_yearly_totals_include_only_their_own_period() = runBlocking {
        service.add(input(amount = 100, at = now), userId) // today
        service.add(input(amount = 200, at = now - day), userId) // yesterday, same month
        service.add(input(amount = 300, at = now - 30 * day), userId) // mid October, same year
        service.add(input(amount = 400, at = now - 400 * day), userId) // previous year

        assertEquals(100L, service.observeTotal(PeriodKind.DAY).first())
        assertEquals(300L, service.observeTotal(PeriodKind.MONTH).first())
        assertEquals(600L, service.observeTotal(PeriodKind.YEAR).first())
        assertEquals(1, service.observeList(PeriodKind.DAY).first().size)
    }
}
