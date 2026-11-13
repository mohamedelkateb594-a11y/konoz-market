package com.konoz.market.domain

import com.konoz.market.data.entity.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who is signed in right now. Lives in the Application, so it survives screen rotation; a cold start always shows the login. */
class AuthSession {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    fun signIn(user: User) {
        _user.value = user
    }

    fun signOut() {
        _user.value = null
    }

    fun can(permission: Permission): Boolean = _user.value?.let { Permissions.has(it.role, permission) } ?: false

    /** Id used to attribute operations (sales, expenses, audit rows). Throws when nobody is signed in. */
    fun requireUserId(): Long = _user.value?.id ?: throw IllegalStateException("No signed-in user")
}
