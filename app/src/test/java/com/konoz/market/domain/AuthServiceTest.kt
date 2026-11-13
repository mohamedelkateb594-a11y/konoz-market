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
import org.junit.Assert.assertNotEquals
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
class AuthServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: AuthService
    private var adminId = 0L
    private val now = 1_700_000_000_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = AuthService(db, { now })
        runBlocking { DatabaseInitializer.seedIfNeeded(db, now) }
        adminId = runBlocking { db.users().findActiveByUsername(DatabaseInitializer.DEFAULT_ADMIN_USERNAME)!!.id }
    }

    @After fun tearDown() = db.close()

    private fun actions() = runBlocking { db.audit().observeRecent(50).first().map { it.action } }

    private suspend fun newUser(username: String = "sara", role: UserRole = UserRole.CASHIER, pin: String = "4321") =
        service.createUser(NewUser("Sara", username, role, pin), adminId)

    // ---------- sign-in ----------

    @Test fun the_seeded_admin_signs_in_with_the_temporary_pin_and_it_is_audited() = runBlocking {
        val user = service.login("admin", "1234")

        assertEquals(adminId, user.id)
        assertEquals(UserRole.ADMIN, user.role)
        assertTrue(actions().contains("LOGIN"))
    }

    @Test fun a_wrong_pin_and_an_unknown_user_fail_the_same_way_and_the_attempt_is_audited() {
        assertThrows(AuthException.InvalidCredentials::class.java) { runBlocking { service.login("admin", "0000") } }
        assertThrows(AuthException.InvalidCredentials::class.java) { runBlocking { service.login("nobody", "1234") } }

        assertEquals(2, actions().count { it == "LOGIN_FAILED" })
        assertFalse(actions().contains("LOGIN"))
    }

    @Test fun the_username_ignores_case_and_surrounding_spaces() = runBlocking {
        assertEquals(adminId, service.login("  ADMIN ", "1234").id)
    }

    @Test fun a_deactivated_user_cannot_sign_in() = runBlocking {
        val id = newUser()
        service.updateUser(id, "Sara", UserRole.CASHIER, isActive = false, actorId = adminId)

        assertThrows(AuthException.InvalidCredentials::class.java) { runBlocking { service.login("sara", "4321") } }
    }

    // ---------- forced PIN change ----------

    @Test fun the_admin_must_change_the_temporary_pin_until_it_does() = runBlocking {
        val admin = service.login("admin", "1234")
        assertTrue(service.mustChangePin(admin))

        service.changePin(adminId, "1234", "5678")

        assertFalse(service.mustChangePin(service.login("admin", "5678")))
        assertEquals("false", db.settings().get(SettingKeys.MUST_CHANGE_ADMIN_PIN))
    }

    @Test fun other_users_are_never_forced_to_change_their_pin() = runBlocking {
        val id = newUser()
        assertFalse(service.mustChangePin(db.users().getById(id)!!))
    }

    // ---------- changing a PIN ----------

    @Test fun changing_the_pin_replaces_hash_and_salt_and_the_old_pin_stops_working() = runBlocking {
        val before = db.users().getById(adminId)!!

        val updated = service.changePin(adminId, "1234", "5678")

        assertNotEquals(before.pinHash, updated.pinHash)
        assertNotEquals(before.salt, updated.salt)
        assertEquals(updated.pinHash, db.users().getById(adminId)!!.pinHash)
        assertThrows(AuthException.InvalidCredentials::class.java) { runBlocking { service.login("admin", "1234") } }
        assertEquals(adminId, service.login("admin", "5678").id)
        assertTrue(actions().contains("PIN_CHANGED"))
    }

    @Test fun a_bad_new_pin_a_wrong_current_pin_or_a_reused_pin_is_rejected_and_nothing_changes() = runBlocking {
        assertThrows(AuthException.InvalidPin::class.java) { runBlocking { service.changePin(adminId, "1234", "12") } }
        assertThrows(AuthException.InvalidPin::class.java) { runBlocking { service.changePin(adminId, "1234", "abcd") } }
        assertThrows(AuthException.InvalidPin::class.java) { runBlocking { service.changePin(adminId, "1234", "123456789") } }
        assertThrows(AuthException.WrongOldPin::class.java) { runBlocking { service.changePin(adminId, "0000", "5678") } }
        assertThrows(AuthException.DefaultPin::class.java) { runBlocking { service.changePin(adminId, "1234", "1234") } }

        service.changePin(adminId, "1234", "5678")
        assertThrows(AuthException.PinUnchanged::class.java) { runBlocking { service.changePin(adminId, "5678", "5678") } }
        assertThrows(AuthException.DefaultPin::class.java) { runBlocking { service.changePin(adminId, "5678", "1234") } }

        assertEquals(adminId, service.login("admin", "5678").id)
    }

    // ---------- user management ----------

    @Test fun creating_a_user_stores_a_hashed_pin_a_normalized_username_and_an_audit_row() = runBlocking {
        val id = service.createUser(NewUser("  Sara  ", "  Sara1 ", UserRole.CASHIER, "4321"), adminId)

        val u = db.users().getById(id)!!
        assertEquals("Sara", u.name)
        assertEquals("sara1", u.username)
        assertEquals(UserRole.CASHIER, u.role)
        assertNotEquals("4321", u.pinHash)
        assertEquals(id, service.login("SARA1", "4321").id)
        assertTrue(actions().contains("USER_CREATED"))
    }

    @Test fun creating_a_user_validates_name_username_pin_and_uniqueness() = runBlocking {
        newUser("sara")

        assertThrows(AuthException.NameRequired::class.java) {
            runBlocking { service.createUser(NewUser("  ", "x1", UserRole.CASHIER, "4321"), adminId) }
        }
        assertThrows(AuthException.UsernameRequired::class.java) {
            runBlocking { service.createUser(NewUser("X", "  ", UserRole.CASHIER, "4321"), adminId) }
        }
        assertThrows(AuthException.InvalidPin::class.java) {
            runBlocking { service.createUser(NewUser("X", "x1", UserRole.CASHIER, "12"), adminId) }
        }
        assertThrows(AuthException.UsernameTaken::class.java) {
            runBlocking { service.createUser(NewUser("Other", "SARA", UserRole.STOCK_KEEPER, "4321"), adminId) }
        }
        assertEquals(2, db.users().count()) // admin + sara only
    }

    @Test fun a_deactivated_username_stays_taken() = runBlocking {
        val id = newUser("sara")
        service.updateUser(id, "Sara", UserRole.CASHIER, isActive = false, actorId = adminId)

        assertThrows(AuthException.UsernameTaken::class.java) { runBlocking { newUser("sara") } }
    }

    @Test fun the_last_active_admin_can_be_neither_demoted_nor_deactivated() = runBlocking {
        assertThrows(AuthException.LastAdmin::class.java) {
            runBlocking { service.updateUser(adminId, "Boss", UserRole.CASHIER, isActive = true, actorId = adminId) }
        }
        assertThrows(AuthException.LastAdmin::class.java) {
            runBlocking { service.updateUser(adminId, "Boss", UserRole.ADMIN, isActive = false, actorId = adminId) }
        }
        assertEquals(UserRole.ADMIN, db.users().getById(adminId)!!.role)
        assertTrue(db.users().getById(adminId)!!.isActive)
    }

    @Test fun an_admin_can_be_demoted_once_another_active_admin_exists() = runBlocking {
        val second = newUser("boss2", UserRole.ADMIN)

        service.updateUser(adminId, "Old admin", UserRole.STOCK_KEEPER, isActive = true, actorId = second)

        val u = db.users().getById(adminId)!!
        assertEquals(UserRole.STOCK_KEEPER, u.role)
        assertEquals("Old admin", u.name)
        assertEquals(1, db.users().countActiveAdmins())
        assertTrue(actions().contains("USER_UPDATED"))
    }

    @Test fun updating_an_unknown_user_or_with_a_blank_name_fails() {
        assertThrows(AuthException.UserNotFound::class.java) {
            runBlocking { service.updateUser(999, "X", UserRole.CASHIER, true, adminId) }
        }
        assertThrows(AuthException.NameRequired::class.java) {
            runBlocking { service.updateUser(adminId, "  ", UserRole.ADMIN, true, adminId) }
        }
    }

    @Test fun an_admin_can_reset_another_users_pin() = runBlocking {
        val id = newUser("sara", pin = "4321")

        service.resetPin(id, "8765", adminId)

        assertThrows(AuthException.InvalidCredentials::class.java) { runBlocking { service.login("sara", "4321") } }
        assertEquals(id, service.login("sara", "8765").id)
        assertTrue(actions().contains("USER_PIN_RESET"))
        assertThrows(AuthException.InvalidPin::class.java) { runBlocking { service.resetPin(id, "1", adminId) } }
        assertThrows(AuthException.UserNotFound::class.java) { runBlocking { service.resetPin(999, "8765", adminId) } }
    }

    @Test fun logging_out_is_audited() = runBlocking {
        service.recordLogout(adminId)

        assertNotNull(db.audit().observeRecent(10).first().firstOrNull { it.action == "LOGOUT" })
        assertNull(db.audit().observeRecent(10).first().firstOrNull { it.action == "LOGIN" })
    }
}
