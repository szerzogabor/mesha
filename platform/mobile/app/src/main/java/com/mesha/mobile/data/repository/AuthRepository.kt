package com.mesha.mobile.data.repository

import com.clerk.api.Clerk
import com.mesha.mobile.ClerkBootstrap
import com.mesha.mobile.data.remote.MeshaApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the Clerk Android SDK's session state into app-level [AuthState]. Clerk owns
 * sign-in/sign-up UI ([com.clerk.ui.auth.AuthView], rendered from
 * [com.mesha.mobile.ui.screens.login.LoginScreen]) and session persistence; this
 * repository only bridges that state into the rest of the app and keeps the backend's
 * user record in sync whenever a session becomes active.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: MeshaApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(
        if (ClerkBootstrap.isReady && Clerk.userFlow.value != null) {
            AuthState.Authenticated
        } else {
            AuthState.Unauthenticated
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        if (ClerkBootstrap.isReady) {
            scope.launch {
                Clerk.userFlow.collect { user ->
                    if (user != null) {
                        runCatching { api.syncUser() }
                        _authState.value = AuthState.Authenticated
                    } else {
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            }
        }
    }

    fun signOut() {
        if (ClerkBootstrap.isReady) {
            scope.launch { Clerk.auth.signOut() }
        }
    }
}

enum class AuthState { Authenticated, Unauthenticated }
