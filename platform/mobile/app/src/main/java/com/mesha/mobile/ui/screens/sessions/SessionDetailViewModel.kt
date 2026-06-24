package com.mesha.mobile.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.AgentSessionDto
import com.mesha.mobile.data.remote.dto.AgentSessionMessageDto
import com.mesha.mobile.data.repository.MeshaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val session: AgentSessionDto? = null,
    val messages: List<AgentSessionMessageDto> = emptyList(),
    val followUp: String = "",
    val sending: Boolean = false,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionDetailUiState())
    val state: StateFlow<SessionDetailUiState> = _state.asStateFlow()

    private var sessionId: String = ""

    fun load(sessionId: String) {
        this.sessionId = sessionId
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val session = meshaRepository.getSession(sessionId).getOrNull()
            val messages = meshaRepository.getSessionMessages(sessionId).getOrNull().orEmpty()
            if (session == null) {
                _state.update { it.copy(loading = false, error = "Session not found") }
            } else {
                _state.update {
                    it.copy(loading = false, session = session, messages = messages)
                }
            }
        }
    }

    fun onFollowUpChange(value: String) = _state.update { it.copy(followUp = value) }

    /** Send a follow-up message to continue the session from mobile. */
    fun sendFollowUp() {
        val content = _state.value.followUp.trim()
        if (content.isBlank() || sessionId.isBlank()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            meshaRepository.sendSessionMessage(sessionId, content).fold(
                onSuccess = { msg ->
                    _state.update {
                        it.copy(sending = false, followUp = "", messages = it.messages + msg)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(sending = false, error = e.message) }
                },
            )
        }
    }
}
