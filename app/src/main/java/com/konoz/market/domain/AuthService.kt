package com.konoz.market.domain

import com.konoz.market.core.PinHasher
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AppSetting
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.seed.DatabaseInitializer
import com.konoz.market.data.seed.SettingKeys
import kotlinx.coroutines.flow.Flow

sealed class AuthException(message: String) : Exception(message) {
    class InvalidCredentials : AuthException("اسم المستخدم أو الرقم السري غير صحيح")
    class InvalidPin : AuthException("الرقم السري يجب أن يكون من 4 إلى 8 أرقام")
    class WrongOldPin : AuthException("الرقم السري الحالي غير صحيح")
    class PinUnchanged : AuthException("الرقم السري الجديد يجب أن يختلف عن الحالي")
    class DefaultPin : AuthException("لا يمكن استخدام الرقم السري الافتراضي")
    class NameRequired : AuthException("اسم المستخدم مطلوب")
    class UsernameRequired : AuthException("اسم الدخول مطلوب")
    class UsernameTaken : AuthException("اسم الدخول مستخدم بالفعل")
    class UserNotFound(val id: Long) : AuthException("المستخدم غير موجود")
    class LastAdmin : AuthException("لا يمكن إيقاف أو تخفيض آخر مدير فعّال")
}

data class NewUser(val name: String, val username: String, val role: UserRole, val pin: String)

/**
 * Sign-in, PIN changes and user management. PINs are PBKDF2-hashed with a per-user salt ([PinHasher]) and never stored in clear.
 * Every change runs in one transaction with its audit-log row; sign-ins (and failed attempts) are audited too.
 * Usernames are trimmed and lower-cased, so " Admin " and "admin" are the same login.
 */
class AuthService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val users get() = db.users()

    /** Checks the credentials of an active user. Wrong username and wrong PIN give the same error. */
    suspend fun login(username: String, pin: String): User {
        val name = normalize(username)
        val user = users.findActiveByUsername(name)
        if (user == null || !PinHasher.verify(pin, user.salt, user.pinHash)) {
            audit(user?.id, "LOGIN_FAILED", user?.id, "username=$name")
            throw AuthException.InvalidCredentials()
        }
        audit(user.id, "LOGIN", user.id, null)
        return user
    }

    suspend fun recordLogout(userId: Long) = audit(userId, "LOGOUT", userId, null)

    /** True while the seeded admin still has its temporary PIN: the app must force a change before anything else. */
    suspend fun mustChangePin(user: User): Boolean =
        user.username == DatabaseInitializer.DEFAULT_ADMIN_USERNAME &&
            db.settings().get(SettingKeys.MUST_CHANGE_ADMIN_PIN) == "true"

    /** A user changes their own PIN. Returns the updated user (new hash and salt). */
    suspend fun changePin(userId: Long, oldPin: String, newPin: String): User = db.tx {
        validatePin(newPin)
        val user = users.getById(userId) ?: throw AuthException.UserNotFound(userId)
        if (!PinHasher.verify(oldPin, user.salt, user.pinHash)) throw AuthException.WrongOldPin()
        if (newPin == oldPin) throw AuthException.PinUnchanged()
        if (newPin == DatabaseInitializer.TEMP_ADMIN_PIN) throw AuthException.DefaultPin()
        val updated = withPin(user, newPin)
        users.update(updated)
        if (user.username == DatabaseInitializer.DEFAULT_ADMIN_USERNAME) {
            db.settings().upsert(AppSetting(SettingKeys.MUST_CHANGE_ADMIN_PIN, "false"))
        }
        audit(userId, "PIN_CHANGED", userId, null)
        updated
    }

    fun observeUsers(): Flow<List<User>> = users.observeAll()

    suspend fun createUser(input: NewUser, actorId: Long): Long = db.tx {
        val name = input.name.trim()
        if (name.isEmpty()) throw AuthException.NameRequired()
        val username = normalize(input.username)
        if (username.isEmpty()) throw AuthException.UsernameRequired()
        validatePin(input.pin)
        if (users.findByUsername(username) != null) throw AuthException.UsernameTaken()
        val salt = PinHasher.newSalt()
        val id = users.insert(
            User(
                name = name, username = username, pinHash = PinHasher.hash(input.pin, salt), salt = salt,
                role = input.role, createdAt = clock(),
            )
        )
        audit(actorId, "USER_CREATED", id, "username=$username role=${input.role.name}")
        id
    }

    /** Edits name, role and active flag. The last active admin can be neither demoted nor deactivated. */
    suspend fun updateUser(id: Long, name: String, role: UserRole, isActive: Boolean, actorId: Long) = db.tx {
        val user = users.getById(id) ?: throw AuthException.UserNotFound(id)
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw AuthException.NameRequired()
        val losesAdmin = user.role == UserRole.ADMIN && user.isActive && (role != UserRole.ADMIN || !isActive)
        if (losesAdmin && users.countActiveAdmins() <= 1) throw AuthException.LastAdmin()
        users.update(user.copy(name = cleanName, role = role, isActive = isActive))
        audit(
            actorId, "USER_UPDATED", id,
            "role=${user.role.name}->${role.name} active=${user.isActive}->$isActive",
        )
    }

    /** An admin sets a new PIN for a user (e.g. a forgotten one). */
    suspend fun resetPin(id: Long, newPin: String, actorId: Long) = db.tx {
        validatePin(newPin)
        val user = users.getById(id) ?: throw AuthException.UserNotFound(id)
        users.update(withPin(user, newPin))
        if (user.username == DatabaseInitializer.DEFAULT_ADMIN_USERNAME && newPin != DatabaseInitializer.TEMP_ADMIN_PIN) {
            db.settings().upsert(AppSetting(SettingKeys.MUST_CHANGE_ADMIN_PIN, "false"))
        }
        audit(actorId, "USER_PIN_RESET", id, null)
    }

    // ---------- internals ----------
    private fun normalize(username: String) = username.trim().lowercase()

    private fun validatePin(pin: String) {
        if (!PIN_PATTERN.matches(pin)) throw AuthException.InvalidPin()
    }

    private fun withPin(user: User, pin: String): User {
        val salt = PinHasher.newSalt()
        return user.copy(pinHash = PinHasher.hash(pin, salt), salt = salt)
    }

    private suspend fun audit(userId: Long?, action: String, entityId: Long?, details: String?) {
        db.audit().insert(AuditLog(userId = userId, action = action, entity = "user", entityId = entityId, details = details, createdAt = clock()))
    }

    private companion object {
        val PIN_PATTERN = Regex("^[0-9]{4,8}$")
    }
}
