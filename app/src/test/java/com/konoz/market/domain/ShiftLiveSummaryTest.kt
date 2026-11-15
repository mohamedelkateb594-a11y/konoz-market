package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftLiveSummaryTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ShiftService
    private var userId = 0L
    private val now = 1_700_000_000_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ShiftService(db, { now })
        userId = runBlocking {
            db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    @Test fun without_an_open_shift_the_live_summary_is_null() = runBlocking {
        assertNull(service.observeLiveSummary().first())
    }

    @Test fun with_an_open_shift_the_live_summary_shows_its_figures() = runBlocking {
        val id = service.open(5_000, userId)

        val s = service.observeLiveSummary().first()!!

        assertEquals(id, s.shift.id)
        assertEquals(5_000L, s.openingCash)
        assertEquals(5_000L, s.expectedCash)
    }

    @Test fun after_closing_there_is_no_live_summary_again() = runBlocking {
        val id = service.open(1_000, userId)
        service.close(id, 1_000, userId)

        assertNull(service.observeLiveSummary().first())
    }
}
