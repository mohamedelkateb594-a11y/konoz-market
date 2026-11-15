package com.konoz.market.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.domain.UserException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UsersError { NAME, USERNAME, TAKEN, PIN, LAST_ADMIN, NOT_ALLOWED, UNKNOWN }

data class AddUserForm(
    val name: String = "",
    val username: String = "",
    val pin: String = "",
    val role: UserRole = UserRole.CASHIER,
)

data class UsersUi(
    val users: List<User> = emptyList(),
    val add: AddUserForm? = null,
    /** The user whose action dialog (reset PIN / activate / role) is open. */
    val actionsFor: User? = null,
    val resetFor: User? = null,
    val resetPin: String = "",
    val error: UsersError? = null,
    val busy: Boolean = false,
)

class UsersViewModel(private val app: KonozApp) : ViewModel() {
    private val _ui = MutableStateFlow(UsersUi())
    val ui: StateFlow<UsersUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.userService.observeAll().collect { list -> _ui.update { it.copy(users = list) } }
        }
    }

    // ---------- add ----------
    fun showAdd() = _ui.update { it.copy(add = AddUserForm(), error = null) }

    fun dismissAdd() = _ui.update { it.copy(add = null, error = null) }

    fun editAdd(transform: (AddUserForm) -> AddUserForm) =
        _ui.update { s -> s.add?.let { s.copy(add = transform(it), error = null) } ?: s }

    fun submitAdd() {
        val form = _ui.value.add ?: return
        run(onDone = { _ui.update { it.copy(add = null) } }) { actor ->
            app.userService.createUser(actor, form.name, form.username, form.pin.filter(Char::isDigit), form.role)
        }
    }

    // ---------- actions on one user ----------
    fun openActions(user: User) = _ui.update { it.copy(actionsFor = user, error = null) }

    fun dismissActions() = _ui.update { it.copy(actionsFor = null, error = null) }

    fun toggleActive(user: User) = run(onDone = { _ui.update { it.copy(actionsFor = null) } }) { actor ->
        app.userService.setActive(actor, user.id, !user.isActive)
    }

    fun changeRole(user: User, role: UserRole) = run(onDone = { _ui.update { it.copy(actionsFor = null) } }) { actor ->
        app.userService.setRole(actor, user.id, role)
    }

    // ---------- reset PIN ----------
    fun openReset(user: User) = _ui.update { it.copy(actionsFor = null, resetFor = user, resetPin = "", error = null) }

    fun dismissReset() = _ui.update { it.copy(resetFor = null, resetPin = "", error = null) }

    fun editResetPin(text: String) = _ui.update { it.copy(resetPin = text.filter(Char::isDigit).take(8), error = null) }

    fun submitReset() {
        val target = _ui.value.resetFor ?: return
        val pin = _ui.value.resetPin
        run(onDone = { _ui.update { it.copy(resetFor = null, resetPin = "") } }) { actor ->
            app.userService.resetPin(actor, target.id, pin)
        }
    }

    /** Runs one user-management call as the signed-in user, maps its failures to [UsersError], and refreshes the session if the user changed themselves. */
    private fun run(onDone: () -> Unit, block: suspend (actorId: Long) -> Unit) {
        if (_ui.value.busy) return
        val actor = app.session.user.value?.id
        if (actor == null) {
            _ui.update { it.copy(error = UsersError.NOT_ALLOWED) }
            return
        }
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block(actor)
                app.database.users().getById(actor)?.let(app.session::signIn) // role or details of the signed-in user may have changed
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserException) {
                _ui.update { it.copy(error = errorOf(e)) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = UsersError.UNKNOWN) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun errorOf(e: UserException): UsersError = when (e) {
        is UserException.InvalidName -> UsersError.NAME
        is UserException.InvalidUsername -> UsersError.USERNAME
        is UserException.UsernameTaken -> UsersError.TAKEN
        is UserException.InvalidPin -> UsersError.PIN
        is UserException.LastAdmin -> UsersError.LAST_ADMIN
        is UserException.NotAllowed -> UsersError.NOT_ALLOWED
        else -> UsersError.UNKNOWN
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { UsersViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
