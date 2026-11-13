package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.seed.DatabaseInitializer
import com.konoz.market.data.seed.SettingKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: UserService
    private var now = 1_700_000_000_000L
    private var adminId = 0L

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = UserService(db, { now })
        DatabaseInitializer.seedIfNeeded(db, now) // admin with the temporary PIN 1234
        adminId = db.users().findActiveByUsername(DatabaseInitializer.DEFAULT_ADMIN_USERNAME)!!.id
    }

    @After fun tearDown() = db.close()

    private suspend fun newCashier(username: String = "sara", pin: String = "4321"): Long =
        service.createUser(adminId, "Sara", username, pin, UserRole.CASHIER)

    private suspend fun actions() = db.audit().observeRecent(50).first().map { it.action }

    // ---------- login ----------
    @Test fun login_with_the_right_pin_returns_the_user_and_audits() = runBlocking {
        val user = service.login(" Admin ", "1234") // username is trimmed and case-insensitive

        assertEquals(adminId, user.id)
        assertTrue(actions().contains("LOGIN_SUCCESS"))
    }

    @Test fun wrong_pin_and_unknown_user_look_the_same_and_count_down_the_attempts() = runBlocking {
        val first = assertThrows(UserException.InvalidCredentials::class.java) { runBlocking { service.login("admin", "0000") } }
        assertEquals(4, first.attemptsLeft)
        val second = assertThrows(UserException.InvalidCredentials::class.java) { runBlocking { service.login("nobody", "1234") } }
        assertEquals(4, second.attemptsLeft) // its own counter
        assertTrue(actions().contains("LOGIN_FAILED"))
    }

    @Test fun five_wrong_pins_lock_the_account_even_for_the_right_pin_until_the_lock_expires() = runBlocking {
        repeat(4) { assertThrows(UserException.InvalidCredentials::class.java) { runBlocking { service.login("admin", "0000") } } }
        val locked = assertThrows(UserException.LockedOut::class.java) { runBlocking { service.login("admin", "0000") } }
        assertEquals(now + UserService.LOCK_MS, locked.untilMillis)
        assertTrue(actions().contains("LOGIN_LOCKED"))

        assertThrows(UserException.LockedOut::class.java) { runBlocking { service.login("admin", "1234") } }
        now += UserService.LOCK_MS + 1

        assertEquals(adminId, service.login("admin", "1234").id)
    }

    @Test fun a_successful_login_resets_the_failure_counter() = runBlocking {
        repeat(3) { assertThrows(UserException.InvalidCredentials::class.java) { runBlocking { service.login("admin", "0000") } } }
        service.login("admin", "1234")

        val ex = assertThrows(UserException.InvalidCredentials::class.java) { runBlocking { service.login("admin", "0000") } }
        assertEquals(4, ex.attemptsLeft)
    }

    // ---------- creating users ----------
    @Test fun an_admin_creates_a_user_who_can_sign_in_and_the_pin_is_not_stored_in_clear() = runBlocking {
        val id = newCashier()

        val stored = db.users().getById(id)!!
        assertEquals(UserRole.CASHIER, stored.role)
        assertTrue(stored.isActive)
        assertFalse(stored.pinHash.contains("4321"))
        assertEquals(id, service.login("sara", "4321").id)
        assertTrue(actions().contains("USER_CREATED"))
    }

    @Test fun create_validates_name_username_pin_and_uniqueness() = runBlocking {
        assertThrows(UserException.InvalidName::class.java) { runBlocking { service.createUser(adminId, "  ", "abc", "1234", UserRole.CASHIER) } }
        assertThrows(UserException.InvalidUsername::class.java) { runBlocking { service.createUser(adminId, "A", "a b", "1234", UserRole.CASHIER) } }
        assertThrows(UserException.InvalidUsername::class.java) { runBlocking { service.createUser(adminId, "A", "ab", "1234", UserRole.CASHIER) } }
        assertThrows(UserException.InvalidPin::class.java) { runBlocking { service.createUser(adminId, "A", "abc", "123", UserRole.CASHIER) } }
        assertThrows(UserException.InvalidPin::class.java) { runBlocking { service.createUser(adminId, "A", "abc", "12ab", UserRole.CASHIER) } }
        newCashier("sara")
        assertThrows(UserException.UsernameTaken::class.java) { runBlocking { newCashier("SARA") } }
        assertEquals(2, db.users().observeAll().first().size) // admin + sara only
    }

    @Test fun only_an_admin_can_manage_users() = runBlocking {
        val cashier = newCashier()

        assertThrows(UserException.NotAllowed::class.java) {
            runBlocking { service.createUser(cashier, "X", "xyz", "1234", UserRole.CASHIER) }
        }
        assertThrows(UserException.NotAllowed::class.java) { runBlocking { service.setRole(cashier, cashier, UserRole.ADMIN) } }
        assertThrows(UserException.NotAllowed::class.java) { runBlocking { service.setActive(cashier, adminId, false) } }
    }

    // ---------- PINs ----------
    @Test fun an_admin_can_reset_anyones_pin_and_the_old_pin_stops_working() = runBlocking {
        val cashier = newCashier()

        service.resetPin(adminId, cashier, "9999")

        assertEquals(cashier, service.login("sara", "9999").id)
        assertThrows(UserException.InvalidCredentials::class.java) { runBlocking { service.login("sara", "4321") } }
        assertTrue(actions().contains("PIN_RESET"))
    }

    @Test fun a_user_can_reset_their_own_pin_but_not_another_users() = runBlocking {
        val cashier = newCashier()

        service.resetPin(cashier, cashier, "5555")
        assertTrue(actions().contains("PIN_CHANGED"))
        assertThrows(UserException.NotAllowed::class.java) { runBlocking { service.resetPin(cashier, adminId, "5555") } }
    }

    @Test fun changing_own_pin_needs_the_current_pin() = runBlocking {
        val cashier = newCashier()

        assertThrows(UserException.WrongOldPin::class.java) { runBlocking { service.changeOwnPin(cashier, "0000", "5555") } }
        service.changeOwnPin(cashier, "4321", "5555")

        assertEquals(cashier, service.login("sara", "5555").id)
    }

    @Test fun the_temporary_admin_pin_prompt_ends_when_the_admin_sets_a_new_pin() = runBlocking {
        assertTrue(service.mustChangeAdminPin())

        service.resetPin(adminId, adminId, "2468")

        assertFalse(service.mustChangeAdminPin())
        assertEquals("false", db.settings().get(SettingKeys.MUST_CHANGE_ADMIN_PIN))
        assertEquals(adminId, service.login("admin", "2468").id)
    }

    // ---------- activation and roles ----------
    @Test fun a_deactivated_user_cannot_sign_in_until_reactivated() = runBlocking {
        val cashier = newCashier()

        service.setActive(adminId, cashier, false)
        assertThrows(UserException.InvalidCredentials::class.java) { runBlocking { service.login("sara", "4321") } }

        service.setActive(adminId, cashier, true)
        assertEquals(cashier, service.login("sara", "4321").id)
    }

    @Test fun an_admin_cannot_deactivate_themselves_or_the_last_active_admin() = runBlocking {
        assertThrows(UserException.NotAllowed::class.java) { runBlocking { service.setActive(adminId, adminId, false) } }

        val second = service.createUser(adminId, "Second", "admin2", "1111", UserRole.ADMIN)
        service.setActive(second, adminId, false) // fine: another admin remains
        assertThrows(UserException.LastAdmin::class.java) { runBlocking { service.setActive(second, adminId, false) } }
    }

    @Test fun the_last_active_admin_cannot_be_demoted_but_can_once_another_admin_exists() = runBlocking {
        assertThrows(UserException.LastAdmin::class.java) { runBlocking { service.setRole(adminId, adminId, UserRole.CASHIER) } }

        val second = service.createUser(adminId, "Second", "admin2", "1111", UserRole.ADMIN)
        service.setRole(second, adminId, UserRole.STOCK_KEEPER)

        assertEquals(UserRole.STOCK_KEEPER, db.users().getById(adminId)!!.role)
        assertTrue(actions().contains("USER_ROLE_CHANGED"))
    }

    @Test fun a_deactivated_actor_can_no_longer_act() = runBlocking {
        val second = service.createUser(adminId, "Second", "admin2", "1111", UserRole.ADMIN)
        service.setActive(adminId, second, false)

        assertThrows(UserException.NotAllowed::class.java) {
            runBlocking { service.createUser(second, "X", "xyz", "1234", UserRole.CASHIER) }
        }
    }
}
