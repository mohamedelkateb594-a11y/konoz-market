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
import kotlinx.coroutines.flow.first

sealed class UserException(message: String) : Exception(message) {
    /** Wrong username or PIN (the two are never told apart). [attemptsLeft] before the account is locked. */
    class InvalidCredentials(val attemptsLeft: Int) : UserException("اسم المستخدم أو الرمز غير صحيح")
    class LockedOut(val untilMillis: Long) : UserException("الحساب مقفل مؤقتًا")
    class InvalidName : UserException("الاسم مطلوب")
    class InvalidUsername : UserException("اسم المستخدم: 3 إلى 20 حرفًا إنجليزيًا صغيرًا أو أرقامًا")
    class UsernameTaken : UserException("اسم المستخدم مستخدم من قبل")
    class InvalidPin : UserException("الرمز يجب أن يكون 4 إلى 8 أرقام")
    class NotFound : UserException("المستخدم غير موجود")
    class WrongOldPin : UserException("الرمز الحالي غير صحيح")
    /** The change would leave the shop without an active admin. */
    class LastAdmin : UserException("لا بد من بقاء مدير نشط واحد على الأقل")
    class NotAllowed : UserException("ليست لديك صلاحية لهذا الإجراء")
}

/**
 * Accounts, PINs and login. PINs are PBKDF2-hashed with a per-user salt ([PinHasher]) and never stored in clear text.
 * Login locks an account for [LOCK_MS] after [MAX_FAILED] wrong PINs in a row; the counter is stored in app settings,
 * so restarting the app does not reset it. Every change runs in one transaction with its audit row, and rules about
 * who may do what are checked here (by the acting user's role in the database), not only in the UI.
 */
class UserService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun observeAll(): Flow<List<User>> = db.users().observeAll()

    suspend fun mustChangeAdminPin(): Boolean = db.settings().get(SettingKeys.MUST_CHANGE_ADMIN_PIN) == "true"

    private sealed interface LoginOutcome {
        data class Success(val user: User) : LoginOutcome
        data class Failed(val attemptsLeft: Int) : LoginOutcome
        data class Locked(val until: Long) : LoginOutcome
    }

    /** Returns the signed-in user, or throws [UserException.InvalidCredentials] / [UserException.LockedOut]. */
    suspend fun login(username: String, pin: String): User {
        val name = username.trim().lowercase()
        val outcome = db.tx {
            val now = clock()
            val key = FAIL_KEY_PREFIX + name
            val (fails, lockedUntil) = parseFailState(db.settings().get(key))
            if (lockedUntil > now) return@tx LoginOutcome.Locked(lockedUntil)
            val user = db.users().findActiveByUsername(name)
            if (user != null && PinHasher.verify(pin, user.salt, user.pinHash)) {
                db.settings().upsert(AppSetting(key, "0|0"))
                audit(user.id, "LOGIN_SUCCESS", user.id, "username=$name", now)
                LoginOutcome.Success(user)
            } else {
                val count = fails + 1
                if (count >= MAX_FAILED) {
                    db.settings().upsert(AppSetting(key, "0|${now + LOCK_MS}"))
                    if (user != null) audit(user.id, "LOGIN_LOCKED", user.id, "username=$name", now)
                    LoginOutcome.Locked(now + LOCK_MS)
                } else {
                    db.settings().upsert(AppSetting(key, "$count|0"))
                    if (user != null) audit(user.id, "LOGIN_FAILED", user.id, "username=$name", now)
                    LoginOutcome.Failed(MAX_FAILED - count)
                }
            }
        }
        return when (outcome) {
            is LoginOutcome.Success -> outcome.user
            is LoginOutcome.Failed -> throw UserException.InvalidCredentials(outcome.attemptsLeft)
            is LoginOutcome.Locked -> throw UserException.LockedOut(outcome.until)
        }
    }

    /** Creates an active user. Only an admin may do this. Returns the new id. */
    suspend fun createUser(actorId: Long, name: String, username: String, pin: String, role: UserRole): Long = db.tx {
        requireManager(actorId)
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw UserException.InvalidName()
        val cleanUsername = username.trim().lowercase()
        if (!USERNAME_REGEX.matches(cleanUsername)) throw UserException.InvalidUsername()
        validatePin(pin)
        if (db.users().observeAll().first().any { it.username.equals(cleanUsername, ignoreCase = true) }) throw UserException.UsernameTaken()
        val now = clock()
        val salt = PinHasher.newSalt()
        val id = db.users().insert(
            User(name = cleanName, username = cleanUsername, pinHash = PinHasher.hash(pin, salt), salt = salt, role = role, createdAt = now)
        )
        audit(actorId, "USER_CREATED", id, "username=$cleanUsername role=${role.name}", now)
        id
    }

    /** Sets a new PIN without asking for the old one: a user for their own account, or an admin for anyone's. */
    suspend fun resetPin(actorId: Long, targetId: Long, newPin: String) = db.tx {
        val actor = activeActor(actorId)
        if (actorId != targetId && !Permissions.has(actor.role, Permission.MANAGE_USERS)) throw UserException.NotAllowed()
        val target = db.users().getById(targetId) ?: throw UserException.NotFound()
        validatePin(newPin)
        storePin(target, newPin)
        audit(actorId, if (actorId == targetId) "PIN_CHANGED" else "PIN_RESET", targetId, "username=${target.username}", clock())
    }

    /** A user changes their own PIN and must give the current one. */
    suspend fun changeOwnPin(userId: Long, oldPin: String, newPin: String) = db.tx {
        val user = activeActor(userId)
        if (!PinHasher.verify(oldPin, user.salt, user.pinHash)) throw UserException.WrongOldPin()
        validatePin(newPin)
        storePin(user, newPin)
        audit(userId, "PIN_CHANGED", userId, "username=${user.username}", clock())
    }

    /** Activates or deactivates an account. An admin cannot deactivate themselves or the last active admin. */
    suspend fun setActive(actorId: Long, targetId: Long, active: Boolean) = db.tx {
        requireManager(actorId)
        val target = db.users().getById(targetId) ?: throw UserException.NotFound()
        if (!active) {
            if (targetId == actorId) throw UserException.NotAllowed()
            if (target.role == UserRole.ADMIN && target.isActive && activeAdminCount() <= 1) throw UserException.LastAdmin()
        }
        db.users().update(target.copy(isActive = active))
        audit(actorId, if (active) "USER_ACTIVATED" else "USER_DEACTIVATED", targetId, "username=${target.username}", clock())
    }

    /** Changes the role of an account. The last active admin cannot be demoted. */
    suspend fun setRole(actorId: Long, targetId: Long, role: UserRole) = db.tx {
        requireManager(actorId)
        val target = db.users().getById(targetId) ?: throw UserException.NotFound()
        if (target.role == UserRole.ADMIN && role != UserRole.ADMIN && target.isActive && activeAdminCount() <= 1) throw UserException.LastAdmin()
        db.users().update(target.copy(role = role))
        audit(actorId, "USER_ROLE_CHANGED", targetId, "username=${target.username} ${target.role.name}->${role.name}", clock())
    }

    // ---------- internals ----------
    private suspend fun activeActor(actorId: Long): User =
        db.users().getById(actorId)?.takeIf { it.isActive } ?: throw UserException.NotAllowed()

    private suspend fun requireManager(actorId: Long) {
        if (!Permissions.has(activeActor(actorId).role, Permission.MANAGE_USERS)) throw UserException.NotAllowed()
    }

    private suspend fun activeAdminCount(): Int =
        db.users().observeAll().first().count { it.role == UserRole.ADMIN && it.isActive }

    private fun validatePin(pin: String) {
        if (!PIN_REGEX.matches(pin)) throw UserException.InvalidPin()
    }

    private suspend fun storePin(target: User, newPin: String) {
        val salt = PinHasher.newSalt()
        db.users().update(target.copy(pinHash = PinHasher.hash(newPin, salt), salt = salt))
        // Once the seeded admin has a PIN of their own, the "change the temporary PIN" prompt is over.
        if (target.username == DatabaseInitializer.DEFAULT_ADMIN_USERNAME && target.role == UserRole.ADMIN) {
            db.settings().upsert(AppSetting(SettingKeys.MUST_CHANGE_ADMIN_PIN, "false"))
        }
    }

    private fun parseFailState(raw: String?): Pair<Int, Long> {
        val parts = raw?.split('|') ?: return 0 to 0L
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    private suspend fun audit(userId: Long, action: String, entityId: Long, details: String, now: Long) {
        db.audit().insert(AuditLog(userId = userId, action = action, entity = "user", entityId = entityId, details = details, createdAt = now))
    }

    companion object {
        const val MAX_FAILED = 5
        const val LOCK_MS = 5 * 60_000L
        private const val FAIL_KEY_PREFIX = "login_fail_"
        private val PIN_REGEX = Regex("^[0-9]{4,8}$")
        private val USERNAME_REGEX = Regex("^[a-z0-9._-]{3,20}$")
    }
}
