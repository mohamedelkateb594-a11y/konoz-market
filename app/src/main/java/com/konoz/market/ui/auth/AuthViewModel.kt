package com.konoz.market.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.seed.DatabaseInitializer
import com.konoz.market.domain.AuthException
import com.konoz.market.domain.AuthService
import com.konoz.market.domain.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginError { CREDENTIALS, UNKNOWN }

data class LoginForm(val username: String = "", val pin: String = "")

/** Sign-in screen state. Lives for the activity, so it is rebuilt after a sign-out (the activity is restarted). */
class AuthViewModel(
    private val auth: AuthService,
    private val session: SessionManager,
    private val ensureSeeded: suspend () -> Unit,
) : ViewModel() {

    private val _form = MutableStateFlow(LoginForm())
    val form: StateFlow<LoginForm> = _form.asStateFlow()

    private val _error = MutableStateFlow<LoginError?>(null)
    val error: StateFlow<LoginError?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** True right after a sign-in with the temporary admin PIN: nothing else is shown until it is changed. */
    private val _forcePinChange = MutableStateFlow(false)
    val forcePinChange: StateFlow<Boolean> = _forcePinChange.asStateFlow()

    fun editUsername(v: String) {
        _error.value = null
        _form.update { it.copy(username = v) }
    }

    fun editPin(v: String) {
        _error.value = null
        _form.update { it.copy(pin = v.filter { c -> c.isDigit() }.take(8)) }
    }

    fun login() {
        if (_busy.value) return
        val f = _form.value
        if (f.username.isBlank() || f.pin.isEmpty()) {
            _error.value = LoginError.CREDENTIALS
            return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                ensureSeeded() // the first-run seed runs in the background: never race it
                val user = auth.login(f.username, f.pin)
                _forcePinChange.value = auth.mustChangePin(user) // set before signing in so the app never flashes
                _form.value = LoginForm()
                session.signIn(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _error.value = LoginError.CREDENTIALS
            } catch (e: Exception) {
                _error.value = LoginError.UNKNOWN
            } finally {
                _busy.value = false
            }
        }
    }

    /** The forced PIN change is done. */
    fun pinChanged() {
        _forcePinChange.value = false
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                AuthViewModel(app.authService, app.session) { DatabaseInitializer.seedIfNeeded(app.database) }
            }
        }
    }
}
