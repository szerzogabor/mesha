package com.mesha.mobile.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.AgentSessionDto
import com.mesha.mobile.data.repository.MeshaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val sessions: List<AgentSessionDto> = emptyList(),
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            meshaRepository.getSessions().fold(
                onSuccess = { s -> _state.update { it.copy(loading = false, sessions = s) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }
}
