package com.mesha.mobile.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.IssueDto
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

data class IssuesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProjectId: String? = null,
    val issues: List<IssueDto> = emptyList(),
)

@HiltViewModel
class IssuesViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(IssuesUiState())
    val state: StateFlow<IssuesUiState> = _state.asStateFlow()

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
                onSuccess = { projects ->
                    val selected = selectionStore.projectId.value
                        ?.takeIf { id -> projects.any { it.id == id } }
                        ?: projects.firstOrNull()?.id
                    _state.update { it.copy(projects = projects, selectedProjectId = selected) }
                    selected?.let { loadIssues(it) } ?: _state.update { it.copy(loading = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                },
            )
        }
    }

    fun selectProject(projectId: String) {
        selectionStore.selectProject(projectId)
        _state.update { it.copy(selectedProjectId = projectId) }
        loadIssues(projectId)
    }

    private fun loadIssues(projectId: String) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            meshaRepository.getIssues(projectId).fold(
                onSuccess = { issues ->
                    _state.update { it.copy(loading = false, issues = issues, error = null) }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                },
            )
        }
    }
}
