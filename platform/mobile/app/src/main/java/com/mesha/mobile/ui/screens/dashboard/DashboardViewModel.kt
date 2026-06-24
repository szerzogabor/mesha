package com.mesha.mobile.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.repository.DraftRepository
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val workspaceName: String = "",
    val openIssues: Int = 0,
    val activeSessions: Int = 0,
    val onlineAgents: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
    draftRepository: DraftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    val unsyncedDrafts: StateFlow<Int> = draftRepository.observeUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val workspaces = meshaRepository.getWorkspaces().getOrNull().orEmpty()
            val workspace = workspaces.firstOrNull {
                it.id == selectionStore.workspaceId.value
            } ?: workspaces.firstOrNull()
            if (workspace == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            selectionStore.selectWorkspace(workspace.id)

            val projects = meshaRepository.getProjects(workspace.id).getOrNull().orEmpty()
            var openIssues = 0
            projects.forEach { project ->
                openIssues += meshaRepository.getIssues(project.id).getOrNull()?.size ?: 0
            }
            val sessions = meshaRepository.getSessions().getOrNull().orEmpty()
            val agents = meshaRepository.getActiveAgents(workspace.id).getOrNull().orEmpty()

            _state.update {
                it.copy(
                    workspaceName = workspace.name,
                    openIssues = openIssues,
                    activeSessions = sessions.count { s ->
                        s.status?.uppercase() !in TERMINAL_STATUSES
                    },
                    onlineAgents = agents.count { a -> a.active },
                    loading = false,
                )
            }
        }
    }

    private companion object {
        val TERMINAL_STATUSES = setOf("COMPLETED", "FAILED", "CANCELLED", "CANCELED")
    }
}
