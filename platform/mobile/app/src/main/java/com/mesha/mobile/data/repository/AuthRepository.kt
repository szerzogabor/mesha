package com.mesha.mobile.data.repository

import com.mesha.mobile.data.local.SecureTokenStore
import com.mesha.mobile.data.remote.MeshaApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the authentication session. The bearer token is a Clerk-issued JWT — Mesha's
 * existing auth. In production the token is obtained from the Clerk Android SDK (see
 * docs/mobile/ARCHITECTURE.md "Authentication"); this repository persists it securely,
 * verifies it against the backend, and exposes auth state to the app.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: MeshaApi,
    private val tokenStore: SecureTokenStore,
) {
    private val _authState = MutableStateFlow(
        if (tokenStore.getToken() != null) AuthState.Authenticated else AuthState.Unauthenticated
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun isAuthenticated(): Boolean = tokenStore.getToken() != null

    /**
     * Persist a Clerk session token, then verify it by syncing the user. On failure the
     * token is discarded so the app does not enter a half-authenticated state.
     */
    suspend fun signIn(clerkSessionToken: String): Result<Unit> {
        tokenStore.saveToken(clerkSessionToken.trim())
        return runCatching {
            api.syncUser()
            _authState.value = AuthState.Authenticated
        }.onFailure {
            tokenStore.clear()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun signOut() {
        tokenStore.clear()
        _authState.value = AuthState.Unauthenticated
    }
}

enum class AuthState { Authenticated, Unauthenticated }
