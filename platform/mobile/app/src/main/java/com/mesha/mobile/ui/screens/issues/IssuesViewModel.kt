package com.mesha.mobile.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the (already-fetched) issue list is ordered client-side — the API has no sort param. */
enum class IssueSort(val label: String) {
    UPDATED("Updated"),
    TITLE("Title"),
    PRIORITY("Priority"),
    STATUS("Status"),
}

data class IssuesUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProjectId: String? = null,
    val statuses: List<ProjectStatusDto> = emptyList(),
    val issues: List<IssueDto> = emptyList(),
    // Filters / search / sort
    val search: String = "",
    val statusFilter: String? = null,
    val priorityFilter: String? = null,
    val sort: IssueSort = IssueSort.UPDATED,
    val sortDescending: Boolean = true,
    // Pagination
    val page: Int = 0,
    val hasMore: Boolean = false,
)

@HiltViewModel
class IssuesViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(IssuesUiState())
    val state: StateFlow<IssuesUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

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
                    if (selected != null) {
                        loadStatuses(selected)
                        loadIssues(selected, reset = true)
                    } else {
                        _state.update { it.copy(loading = false) }
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                },
            )
        }
    }

    fun selectProject(projectId: String) {
        selectionStore.selectProject(projectId)
        _state.update {
            it.copy(
                selectedProjectId = projectId,
                statusFilter = null,
                priorityFilter = null,
                search = "",
            )
        }
        loadStatuses(projectId)
        loadIssues(projectId, reset = true)
    }

    private fun loadStatuses(projectId: String) {
        viewModelScope.launch {
            meshaRepository.getProjectStatuses(projectId).onSuccess { statuses ->
                _state.update { it.copy(statuses = statuses.sortedBy { s -> s.position ?: 0 }) }
            }
        }
    }

    // --- Filters / search / sort ---

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
        // Debounce so we don't fire a request on every keystroke.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            _state.value.selectedProjectId?.let { loadIssues(it, reset = true) }
        }
    }

    fun setStatusFilter(status: String?) {
        _state.update { it.copy(statusFilter = status) }
        _state.value.selectedProjectId?.let { loadIssues(it, reset = true) }
    }

    fun setPriorityFilter(priority: String?) {
        _state.update { it.copy(priorityFilter = priority) }
        _state.value.selectedProjectId?.let { loadIssues(it, reset = true) }
    }

    fun setSort(sort: IssueSort) {
        _state.update {
            if (it.sort == sort) it.copy(sortDescending = !it.sortDescending)
            else it.copy(sort = sort, sortDescending = true)
        }
        _state.update { it.copy(issues = sorted(it.issues)) }
    }

    // --- Loading ---

    private fun loadIssues(projectId: String, reset: Boolean) {
        val s = _state.value
        val page = if (reset) 0 else s.page + 1
        _state.update {
            if (reset) it.copy(loading = true, error = null)
            else it.copy(loadingMore = true)
        }
        viewModelScope.launch {
            meshaRepository.getIssuesPaged(
                projectId = projectId,
                status = s.statusFilter,
                priority = s.priorityFilter,
                search = s.search.trim().takeIf { it.isNotBlank() },
                page = page,
            ).fold(
                onSuccess = { paged ->
                    _state.update {
                        val merged = if (reset) paged.content else it.issues + paged.content
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            issues = sorted(merged),
                            page = paged.page,
                            hasMore = !paged.last,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, loadingMore = false, error = e.message) }
                },
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || !s.hasMore) return
        s.selectedProjectId?.let { loadIssues(it, reset = false) }
    }

    private fun sorted(issues: List<IssueDto>): List<IssueDto> {
        val comparator: Comparator<IssueDto> = when (_state.value.sort) {
            IssueSort.TITLE -> compareBy { it.title.lowercase() }
            IssueSort.STATUS -> compareBy { it.status ?: "" }
            IssueSort.PRIORITY -> compareBy { priorityRank(it.priority) }
            IssueSort.UPDATED -> compareBy { it.updatedAt ?: it.createdAt ?: "" }
        }
        val ordered = issues.sortedWith(comparator)
        return if (_state.value.sortDescending) ordered.reversed() else ordered
    }

    private fun priorityRank(priority: String?): Int = when (priority?.uppercase()) {
        "URGENT" -> 4
        "HIGH" -> 3
        "MEDIUM" -> 2
        "LOW" -> 1
        else -> 0
    }
}
