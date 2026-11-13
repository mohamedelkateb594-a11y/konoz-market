package com.konoz.market.domain

import com.konoz.market.data.entity.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in user, in memory only: the app asks for the PIN again after the process is killed.
 * Lives in the Application, so every screen and ViewModel reads the same user.
 */
class SessionManager {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    fun signIn(user: User) {
        _user.value = user
    }

    fun signOut() {
        _user.value = null
    }

    fun can(permission: Permission): Boolean = _user.value?.let { Permissions.has(it.role, permission) } ?: false
}
