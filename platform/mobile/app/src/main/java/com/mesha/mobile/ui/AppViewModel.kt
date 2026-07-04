package com.mesha.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.repository.AuthRepository
import com.mesha.mobile.data.repository.AuthState
import com.mesha.mobile.notifications.PushNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val pushNotificationManager: PushNotificationManager,
) : ViewModel() {
    val authState = authRepository.authState

    init {
        // Once signed in, make sure this device's push token is registered with the
        // backend (no-op if the user turned notifications off). Registration needs the
        // Clerk bearer token, which only exists after authentication.
        viewModelScope.launch {
            authState.distinctUntilChanged().collect { state ->
                if (state == AuthState.Authenticated) {
                    pushNotificationManager.syncRegistration()
                }
            }
        }
    }

    fun signOut() = authRepository.signOut()
}
