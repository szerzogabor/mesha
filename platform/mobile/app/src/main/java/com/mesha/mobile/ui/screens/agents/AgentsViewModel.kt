package com.mesha.mobile.ui.screens.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.AssignableAgentDto
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val agents: List<AssignableAgentDto> = emptyList(),
)

@HiltViewModel
class AgentsViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentsUiState())
    val state: StateFlow<AgentsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val wsId = selectionStore.workspaceId.value
                ?: meshaRepository.getWorkspaces().getOrNull()?.firstOrNull()?.id
                    ?.also { selectionStore.selectWorkspace(it) }
            if (wsId == null) {
                _state.update { it.copy(loading = false, error = "No workspace available") }
                return@launch
            }
            meshaRepository.getActiveAgents(wsId).fold(
                onSuccess = { a -> _state.update { it.copy(loading = false, agents = a) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }
}
