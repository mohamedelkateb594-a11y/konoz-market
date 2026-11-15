package com.konoz.market.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.seed.DatabaseInitializer
import com.konoz.market.data.seed.SettingKeys
import com.konoz.market.domain.UserException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStage { SIGNED_OUT, MUST_CHANGE_PIN, READY }

enum class AuthError { INVALID, LOCKED, PIN, MISMATCH, UNKNOWN }

/** Login form. [attemptsLeft] belongs to INVALID, [lockedMinutes] to LOCKED. */
data class LoginUi(
    val username: String = "",
    val pin: String = "",
    val busy: Boolean = false,
    val error: AuthError? = null,
    val attemptsLeft: Int = 0,
    val lockedMinutes: Int = 0,
)

/** The "choose your own PIN" form shown after the first login with the temporary admin PIN. */
data class ForcePinUi(
    val pin: String = "",
    val confirm: String = "",
    val busy: Boolean = false,
    val error: AuthError? = null,
)

class AuthViewModel(private val app: KonozApp, private val clock: () -> Long = { System.currentTimeMillis() }) : ViewModel() {
    private val session = app.session

    /** Signed out -> login. Signed in as the seeded admin that still has the temporary PIN -> forced PIN change. Otherwise the app. */
    val stage: StateFlow<AuthStage> = combine(session.user, app.database.settings().observeAll()) { user, settings ->
        val mustChange = settings.firstOrNull { it.key == SettingKeys.MUST_CHANGE_ADMIN_PIN }?.value == "true"
        when {
            user == null -> AuthStage.SIGNED_OUT
            mustChange && user.role == UserRole.ADMIN && user.username == DatabaseInitializer.DEFAULT_ADMIN_USERNAME -> AuthStage.MUST_CHANGE_PIN
            else -> AuthStage.READY
        }
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        if (session.user.value == null) AuthStage.SIGNED_OUT else AuthStage.READY,
    )

    private val _login = MutableStateFlow(LoginUi())
    val login: StateFlow<LoginUi> = _login.asStateFlow()

    private val _force = MutableStateFlow(ForcePinUi())
    val force: StateFlow<ForcePinUi> = _force.asStateFlow()

    fun onUsername(text: String) = _login.update { it.copy(username = text, error = null) }

    fun onPin(text: String) = _login.update { it.copy(pin = text.filter(Char::isDigit).take(8), error = null) }

    fun login() {
        val form = _login.value
        if (form.busy) return
        if (form.username.isBlank() || form.pin.isEmpty()) {
            _login.update { it.copy(error = AuthError.INVALID, attemptsLeft = 0) }
            return
        }
        _login.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                app.ensureSeeded()
                val user = app.userService.login(form.username, form.pin)
                _login.value = LoginUi()
                session.signIn(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserException.InvalidCredentials) {
                _login.update { it.copy(busy = false, pin = "", error = AuthError.INVALID, attemptsLeft = e.attemptsLeft) }
            } catch (e: UserException.LockedOut) {
                val minutes = ((e.untilMillis - clock() + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                _login.update { it.copy(busy = false, pin = "", error = AuthError.LOCKED, lockedMinutes = minutes) }
            } catch (e: Exception) {
                _login.update { it.copy(busy = false, error = AuthError.UNKNOWN) }
            }
        }
    }

    fun onForcePin(text: String) = _force.update { it.copy(pin = text.filter(Char::isDigit).take(8), error = null) }

    fun onForceConfirm(text: String) = _force.update { it.copy(confirm = text.filter(Char::isDigit).take(8), error = null) }

    /** Saves the new PIN of the signed-in admin; the stage then moves on to READY by itself (the setting flips to false). */
    fun saveForcedPin() {
        val form = _force.value
        val user = session.user.value ?: return
        if (form.busy) return
        if (form.pin != form.confirm) {
            _force.update { it.copy(error = AuthError.MISMATCH) }
            return
        }
        _force.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                app.userService.resetPin(user.id, user.id, form.pin)
                _force.value = ForcePinUi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserException.InvalidPin) {
                _force.update { it.copy(busy = false, error = AuthError.PIN) }
            } catch (e: Exception) {
                _force.update { it.copy(busy = false, error = AuthError.UNKNOWN) }
            }
        }
    }

    fun signOut() {
        _login.value = LoginUi()
        _force.value = ForcePinUi()
        session.signOut()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AuthViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
