package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpenseIdempotencyTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ExpenseService
    private var userId = 0L
    private val now = 1_700_000_000_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ExpenseService(db, { now }, TimeZone.getTimeZone("UTC"))
        userId = runBlocking {
            db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    private fun input(uuid: String) = ExpenseInput(5_000, ExpenseCategory.WATER, "Water bill", uuid = uuid)

    @Test fun adding_the_same_uuid_twice_creates_one_expense_and_one_audit_row() = runBlocking {
        val first = service.add(input("form-1"), userId)
        val second = service.add(input("form-1"), userId)

        assertEquals(first, second)
        assertEquals(1, db.expenses().observeBetween(0, Long.MAX_VALUE).first().size)
        assertEquals(1, db.audit().observeRecent(10).first().size)
    }

    @Test fun different_uuids_create_different_expenses() = runBlocking {
        val a = service.add(input("form-1"), userId)
        val b = service.add(input("form-2"), userId)

        assertNotEquals(a, b)
        assertEquals(2, db.expenses().observeBetween(0, Long.MAX_VALUE).first().size)
    }

    @Test fun inputs_without_an_explicit_uuid_are_never_de_duplicated() = runBlocking {
        service.add(ExpenseInput(100, ExpenseCategory.MISC, "x"), userId)
        service.add(ExpenseInput(100, ExpenseCategory.MISC, "x"), userId)
        assertEquals(2, db.expenses().observeBetween(0, Long.MAX_VALUE).first().size)
    }
}
