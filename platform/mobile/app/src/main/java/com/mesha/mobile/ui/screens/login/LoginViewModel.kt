package com.mesha.mobile.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val token: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onTokenChange(value: String) {
        _state.update { it.copy(token = value, error = null) }
    }

    fun signIn() {
        val token = _state.value.token.trim()
        if (token.isBlank()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            authRepository.signIn(token).fold(
                onSuccess = { _state.update { it.copy(loading = false, signedIn = true) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Sign-in failed")
                    }
                },
            )
        }
    }
}
