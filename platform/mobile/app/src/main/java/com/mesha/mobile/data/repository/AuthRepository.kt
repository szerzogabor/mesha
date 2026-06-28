package com.mesha.mobile.data.repository

import com.clerk.api.Clerk
import com.mesha.mobile.ClerkBootstrap
import com.mesha.mobile.data.remote.MeshaApi
import com.clerk.api.user.User
import com.mesha.mobile.data.remote.dto.SyncUserRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the Clerk Android SDK's session state into app-level [AuthState]. Clerk owns
 * sign-in/sign-up UI ([com.clerk.ui.auth.AuthView], rendered from
 * [com.mesha.mobile.ui.screens.login.LoginScreen]) and session persistence; this
 * repository only bridges that state into the rest of the app and keeps the backend's
 * user record in sync whenever a session becomes active.
 *
 * State is held at [AuthState.Loading] until [Clerk.isInitialized] flips to `true`. That
 * flow only completes once Clerk has fetched the instance's `Environment` (which strategies
 * - email, username, OAuth providers - are enabled). [com.clerk.ui.auth.AuthView] reads that
 * environment as a plain field, not a Compose state, so composing it before the fetch
 * finishes renders permanently blank (header only, no input field or social buttons) since
 * nothing later forces it to recompose. Gating on [Clerk.isInitialized] instead of just
 * [ClerkBootstrap.isReady] (which flips synchronously, before the fetch even starts) avoids
 * that race.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: MeshaApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(initialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        if (ClerkBootstrap.isReady) {
            scope.launch {
                combine(Clerk.isInitialized, Clerk.userFlow) { initialized, user -> initialized to user }
                    .collect { (initialized, user) ->
                        _authState.value = when {
                            !initialized -> AuthState.Loading
                            user == null -> AuthState.Unauthenticated
                            else -> {
                                if (_authState.value != AuthState.Authenticated) {
                                    syncUser(user)
                                }
                                AuthState.Authenticated
                            }
                        }
                    }
            }
        }
    }

    // Awaited inline, not fire-and-forget: the backend rejects every other endpoint with 401
    // ("User not found, please sync your account") until this sync has created the user row,
    // so screens that fetch data right after AuthState.Authenticated need that row to exist.
    private suspend fun syncUser(user: User) {
        val primaryEmail = user.emailAddresses
            ?.firstOrNull { it.id == user.primaryEmailAddressId }
            ?.emailAddress
            ?: user.emailAddresses?.firstOrNull()?.emailAddress
        if (primaryEmail != null) {
            val name = listOfNotNull(user.firstName, user.lastName)
                .joinToString(" ")
                .ifBlank { null }
            runCatching { api.syncUser(SyncUserRequestDto(email = primaryEmail, name = name)) }
        }
    }

    fun signOut() {
        if (ClerkBootstrap.isReady) {
            scope.launch { Clerk.auth.signOut() }
        }
    }
}

private fun initialAuthState(): AuthState = when {
    !ClerkBootstrap.isReady -> AuthState.Unauthenticated
    !Clerk.isInitialized.value -> AuthState.Loading
    Clerk.userFlow.value != null -> AuthState.Authenticated
    else -> AuthState.Unauthenticated
}

enum class AuthState { Loading, Authenticated, Unauthenticated }
