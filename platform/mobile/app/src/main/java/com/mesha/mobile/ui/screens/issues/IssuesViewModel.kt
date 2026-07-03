package com.mesha.mobile.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.UpdateIssueRequestDto
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

/** List (paginated) vs. Board (all issues grouped into status columns). */
enum class IssueViewMode { LIST, BOARD }

/** When the board loads every issue at once, cap the pages we'll page through. */
private const val BOARD_PAGE_SIZE = 100
private const val MAX_BOARD_PAGES = 20

data class IssuesUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProjectId: String? = null,
    val statuses: List<ProjectStatusDto> = emptyList(),
    val issues: List<IssueDto> = emptyList(),
    // List vs. board
    val viewMode: IssueViewMode = IssueViewMode.LIST,
    val moveError: String? = null,
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
    private var issuesJob: Job? = null

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
        reload(projectId)
    }

    fun setViewMode(mode: IssueViewMode) {
        if (_state.value.viewMode == mode) return
        _state.update { it.copy(viewMode = mode) }
        // The board needs every issue at once (no pagination), so reload accordingly.
        _state.value.selectedProjectId?.let { reload(it) }
    }

    /** Reload issues for the active view: list = first page (paginated), board = all pages. */
    private fun reload(projectId: String) {
        if (_state.value.viewMode == IssueViewMode.BOARD) loadAllIssues(projectId)
        else loadIssues(projectId, reset = true)
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
            _state.value.selectedProjectId?.let { reload(it) }
        }
    }

    fun setStatusFilter(status: String?) {
        _state.update { it.copy(statusFilter = status) }
        _state.value.selectedProjectId?.let { reload(it) }
    }

    fun setPriorityFilter(priority: String?) {
        _state.update { it.copy(priorityFilter = priority) }
        _state.value.selectedProjectId?.let { reload(it) }
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
        // A new query/filter supersedes any in-flight load so stale results can't overwrite it.
        if (reset) issuesJob?.cancel()
        _state.update {
            if (reset) it.copy(loading = true, error = null)
            else it.copy(loadingMore = true)
        }
        issuesJob = viewModelScope.launch {
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

    /**
     * Board view groups issues into status columns, so it needs the full set rather than one page.
     * Page through the project's issues (capped at [MAX_BOARD_PAGES]) and load them all at once.
     * The status filter is intentionally ignored here — every column is shown.
     */
    private fun loadAllIssues(projectId: String) {
        issuesJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        issuesJob = viewModelScope.launch {
            val s = _state.value
            val all = mutableListOf<IssueDto>()
            var page = 0
            while (page < MAX_BOARD_PAGES) {
                val result = meshaRepository.getIssuesPaged(
                    projectId = projectId,
                    priority = s.priorityFilter,
                    search = s.search.trim().takeIf { it.isNotBlank() },
                    page = page,
                    size = BOARD_PAGE_SIZE,
                )
                val paged = result.getOrElse { e ->
                    _state.update { it.copy(loading = false, loadingMore = false, error = e.message) }
                    return@launch
                }
                all += paged.content
                if (paged.last) break
                page++
            }
            _state.update {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    issues = sorted(all),
                    hasMore = false,
                    error = null,
                )
            }
        }
    }

    /**
     * Move an issue to a new status (the board's drag/tap action). Optimistically updates the
     * local card, then persists; on failure the card snaps back and [IssuesUiState.moveError]
     * is set (e.g. a ticket-rule violation).
     */
    fun moveIssueStatus(issueId: String, newStatus: String) {
        val projectId = _state.value.selectedProjectId ?: return
        val original = _state.value.issues.firstOrNull { it.id == issueId } ?: return
        if (original.status == newStatus) return
        _state.update {
            it.copy(
                moveError = null,
                issues = it.issues.map { i -> if (i.id == issueId) i.copy(status = newStatus) else i },
            )
        }
        viewModelScope.launch {
            meshaRepository.updateIssue(projectId, issueId, UpdateIssueRequestDto(status = newStatus)).fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(issues = it.issues.map { i -> if (i.id == issueId) updated else i })
                    }
                },
                onFailure = { e ->
                    // Revert the optimistic change.
                    _state.update {
                        it.copy(
                            issues = it.issues.map { i -> if (i.id == issueId) original else i },
                            moveError = e.message ?: "Couldn't move issue",
                        )
                    }
                },
            )
        }
    }

    fun clearMoveError() = _state.update { it.copy(moveError = null) }

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
