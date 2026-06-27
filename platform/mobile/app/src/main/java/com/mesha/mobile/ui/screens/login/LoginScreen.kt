package com.mesha.mobile.ui.screens.login

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clerk.ui.auth.AuthView

/**
 * Sign-in / sign-up screen. Clerk's [AuthView] renders the full hosted auth flow (email,
 * OAuth, passkeys, MFA) and persists the resulting session itself.
 * [com.mesha.mobile.data.repository.AuthRepository] observes that session state and
 * flips [com.mesha.mobile.data.repository.AuthState] once it's established, which is
 * what takes the user off this screen — see [com.mesha.mobile.ui.MeshaApp].
 */
@Composable
fun LoginScreen() {
    AuthView(
        modifier = Modifier.fillMaxSize(),
        isDismissible = true,
        mode = com.clerk.ui.auth.AuthMode.SIGN_IN_OR_UP
    )
}
