package com.mesha.mobile.ui.screens.createissue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.CreateIssueRequestDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.WorkspaceMemberDto
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateIssueManualUiState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    // Options
    val projects: List<ProjectDto> = emptyList(),
    val statuses: List<ProjectStatusDto> = emptyList(),
    val members: List<WorkspaceMemberDto> = emptyList(),
    val labels: List<LabelDto> = emptyList(),
    // Form fields
    val selectedProjectId: String? = null,
    val title: String = "",
    val description: String = "",
    val status: String? = null,
    val priority: String = "MEDIUM",
    val assigneeId: String? = null,
    val selectedLabelIds: Set<String> = emptySet(),
)

/** Drives the manual (non-AI) create-issue form with assignee / status / label selection. */
@HiltViewModel
class CreateIssueManualViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateIssueManualUiState())
    val state: StateFlow<CreateIssueManualUiState> = _state.asStateFlow()

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
            val projects = meshaRepository.getProjects(wsId).getOrNull().orEmpty()
            val members = meshaRepository.getWorkspaceMembers(wsId).getOrNull().orEmpty()
            val labels = meshaRepository.getLabels(wsId).getOrNull().orEmpty()
            val selected = selectionStore.projectId.value
                ?.takeIf { id -> projects.any { it.id == id } }
                ?: projects.firstOrNull()?.id
            _state.update {
                it.copy(
                    loading = false,
                    projects = projects,
                    members = members,
                    labels = labels,
                    selectedProjectId = selected,
                )
            }
            selected?.let { loadStatuses(it) }
        }
    }

    private fun loadStatuses(projectId: String) {
        viewModelScope.launch {
            val statuses = meshaRepository.getProjectStatuses(projectId).getOrNull().orEmpty()
                .sortedBy { it.position ?: 0 }
            _state.update {
                it.copy(
                    statuses = statuses,
                    status = it.status ?: statuses.firstOrNull()?.name,
                )
            }
        }
    }

    fun selectProject(id: String) {
        selectionStore.selectProject(id)
        _state.update { it.copy(selectedProjectId = id, status = null) }
        loadStatuses(id)
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v, error = null) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setStatus(v: String?) = _state.update { it.copy(status = v) }
    fun setPriority(v: String) = _state.update { it.copy(priority = v) }
    fun setAssignee(id: String?) = _state.update { it.copy(assigneeId = id) }

    fun toggleLabel(id: String) = _state.update {
        it.copy(
            selectedLabelIds = if (it.selectedLabelIds.contains(id)) it.selectedLabelIds - id
            else it.selectedLabelIds + id,
        )
    }

    fun submit() {
        val s = _state.value
        val projectId = s.selectedProjectId
        if (projectId == null) {
            _state.update { it.copy(error = "Select a project") }
            return
        }
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "Title is required") }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            meshaRepository.createIssue(
                projectId,
                CreateIssueRequestDto(
                    title = s.title.trim(),
                    description = s.description.trim().takeIf { it.isNotBlank() },
                    status = s.status,
                    priority = s.priority,
                    assigneeId = s.assigneeId,
                    labelIds = s.selectedLabelIds.toList().takeIf { it.isNotEmpty() },
                ),
            ).fold(
                onSuccess = { _state.update { it.copy(submitting = false, done = true) } },
                onFailure = { e -> _state.update { it.copy(submitting = false, error = e.message) } },
            )
        }
    }
}
