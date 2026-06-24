package com.mesha.mobile.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val projects: List<ProjectDto> = emptyList(),
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

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
            meshaRepository.getProjects(wsId).fold(
                onSuccess = { p -> _state.update { it.copy(loading = false, projects = p) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }
}
