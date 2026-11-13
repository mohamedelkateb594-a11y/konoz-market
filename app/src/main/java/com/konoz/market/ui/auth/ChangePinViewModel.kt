package com.konoz.market.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.domain.AuthException
import com.konoz.market.domain.AuthService
import com.konoz.market.domain.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PinError { FORMAT, WRONG_OLD, UNCHANGED, DEFAULT, MISMATCH, UNKNOWN }

data class PinForm(val current: String = "", val newPin: String = "", val confirm: String = "")

/** The signed-in user changes their own PIN (forced at first sign-in of the seeded admin, or voluntary from the More menu). */
class ChangePinViewModel(
    private val auth: AuthService,
    private val session: SessionManager,
) : ViewModel() {

    private val _form = MutableStateFlow(PinForm())
    val form: StateFlow<PinForm> = _form.asStateFlow()

    private val _error = MutableStateFlow<PinError?>(null)
    val error: StateFlow<PinError?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun edit(transform: (PinForm) -> PinForm) {
        _error.value = null
        _form.update(transform)
    }

    fun save() {
        if (_busy.value) return
        val user = session.user.value ?: return
        val f = _form.value
        if (f.newPin != f.confirm) {
            _error.value = PinError.MISMATCH
            return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                val updated = auth.changePin(user.id, f.current, f.newPin)
                session.signIn(updated)
                _form.value = PinForm()
                _done.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _error.value = when (e) {
                    is AuthException.InvalidPin -> PinError.FORMAT
                    is AuthException.WrongOldPin -> PinError.WRONG_OLD
                    is AuthException.PinUnchanged -> PinError.UNCHANGED
                    is AuthException.DefaultPin -> PinError.DEFAULT
                    else -> PinError.UNKNOWN
                }
            } catch (e: Exception) {
                _error.value = PinError.UNKNOWN
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                ChangePinViewModel(app.authService, app.session)
            }
        }
    }
}
