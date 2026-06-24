package com.mesha.mobile.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sign-in screen. The bearer token is a Clerk session JWT (Mesha's existing auth).
 * In production the token is provided by the Clerk Android SDK via a hosted sign-in
 * flow; this screen also accepts a pasted session token as a fallback / for testing.
 * See docs/mobile/ARCHITECTURE.md → Authentication.
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Mesha", style = MaterialTheme.typography.headlineLarge)
        Text(
            "AI-native project management",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )

        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::onTokenChange,
            label = { Text("Clerk session token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = viewModel::signIn,
            enabled = !state.loading && state.token.isNotBlank(),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Sign in")
        }
    }
}
