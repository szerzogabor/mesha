package com.mesha.mobile.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.ActivityEventDto
import com.mesha.mobile.data.remote.dto.AssignableAgentDto
import com.mesha.mobile.data.remote.dto.BlocksSessionDto
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.IssueAgentDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.UpdateIssueRequestDto
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

private const val DEFAULT_LABEL_COLOR = "#6366F1"

data class IssueDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val issue: IssueDto? = null,
    val comments: List<CommentDto> = emptyList(),
    val activity: List<ActivityEventDto> = emptyList(),
    // Per-project custom statuses (with colors)
    val statuses: List<ProjectStatusDto> = emptyList(),
    // Assignee / labels / agents options
    val members: List<WorkspaceMemberDto> = emptyList(),
    val workspaceLabels: List<LabelDto> = emptyList(),
    val issueAgents: List<IssueAgentDto> = emptyList(),
    val activeAgents: List<AssignableAgentDto> = emptyList(),
    val blocksSessions: List<BlocksSessionDto> = emptyList(),
    // Comment input
    val commentInput: String = "",
    val sendingComment: Boolean = false,
    val replyingToId: String? = null,
    // Pickers
    val showStatusPicker: Boolean = false,
    val updatingStatus: Boolean = false,
    val showPriorityPicker: Boolean = false,
    val updatingPriority: Boolean = false,
    val showAssigneePicker: Boolean = false,
    val updatingAssignee: Boolean = false,
    val showLabelEditor: Boolean = false,
    val updatingLabels: Boolean = false,
    val showAgentPicker: Boolean = false,
    val updatingAgents: Boolean = false,
    // New-label inline creation
    val newLabelName: String = "",
    // Start AI session dialog
    val showStartSession: Boolean = false,
    val sessionInstructions: String = "",
    val startingSession: Boolean = false,
    // Inline edit mode
    val editMode: Boolean = false,
    val editTitle: String = "",
    val editDescription: String = "",
    val submittingEdit: Boolean = false,
    // Delete
    val showDeleteConfirm: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val updateError: String? = null,
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(IssueDetailUiState())
    val state: StateFlow<IssueDetailUiState> = _state.asStateFlow()

    private var projectId: String = ""
    private var issueId: String = ""

    fun load(projectId: String, issueId: String) {
        this.projectId = projectId
        this.issueId = issueId
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val issue = meshaRepository.getIssue(projectId, issueId).getOrNull()
            val comments = meshaRepository.getComments(issueId).getOrNull().orEmpty()
            val statuses = meshaRepository.getProjectStatuses(projectId).getOrNull().orEmpty()
                .sortedBy { it.position ?: 0 }
            val activity = meshaRepository.getIssueActivity(projectId, issueId).getOrNull().orEmpty()
            val issueAgents = meshaRepository.getIssueAgents(projectId, issueId).getOrNull().orEmpty()
            val blocksSessions = meshaRepository.getBlocksSessions(projectId, issueId).getOrNull().orEmpty()
            if (issue == null) {
                _state.update { it.copy(loading = false, error = "Issue not found") }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        issue = issue,
                        comments = comments,
                        statuses = statuses,
                        activity = activity,
                        issueAgents = issueAgents,
                        blocksSessions = blocksSessions,
                    )
                }
            }
        }
    }

    /** Resolve the workspace id (from the in-memory selection, else first workspace). */
    private suspend fun workspaceId(): String? =
        selectionStore.workspaceId.value
            ?: meshaRepository.getWorkspaces().getOrNull()?.firstOrNull()?.id
                ?.also { selectionStore.selectWorkspace(it) }

    // --- Comment ---

    fun setCommentInput(text: String) {
        _state.update { it.copy(commentInput = text) }
    }

    fun startReply(commentId: String?) = _state.update { it.copy(replyingToId = commentId) }

    fun sendComment() {
        val body = _state.value.commentInput.trim()
        if (body.isBlank() || issueId.isBlank()) return
        val parentId = _state.value.replyingToId
        _state.update { it.copy(sendingComment = true) }
        viewModelScope.launch {
            meshaRepository.addComment(issueId, body, parentId).fold(
                onSuccess = {
                    // Re-fetch to get the correctly-threaded tree from the server.
                    val comments = meshaRepository.getComments(issueId).getOrNull().orEmpty()
                    _state.update {
                        it.copy(
                            sendingComment = false,
                            commentInput = "",
                            replyingToId = null,
                            comments = comments,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(sendingComment = false, updateError = e.message) }
                },
            )
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            meshaRepository.deleteComment(issueId, commentId).fold(
                onSuccess = {
                    val comments = meshaRepository.getComments(issueId).getOrNull().orEmpty()
                    _state.update { it.copy(comments = comments) }
                },
                onFailure = { e -> _state.update { it.copy(updateError = e.message) } },
            )
        }
    }

    // --- Status ---

    fun showStatusPicker() = _state.update { it.copy(showStatusPicker = true) }
    fun dismissStatusPicker() = _state.update { it.copy(showStatusPicker = false) }

    fun updateStatus(status: String) {
        _state.update { it.copy(showStatusPicker = false, updatingStatus = true) }
        patch(UpdateIssueRequestDto(status = status)) { it.copy(updatingStatus = false) }
    }

    // --- Priority ---

    fun showPriorityPicker() = _state.update { it.copy(showPriorityPicker = true) }
    fun dismissPriorityPicker() = _state.update { it.copy(showPriorityPicker = false) }

    fun updatePriority(priority: String) {
        _state.update { it.copy(showPriorityPicker = false, updatingPriority = true) }
        patch(UpdateIssueRequestDto(priority = priority)) { it.copy(updatingPriority = false) }
    }

    // --- Assignee ---

    fun showAssigneePicker() {
        _state.update { it.copy(showAssigneePicker = true) }
        if (_state.value.members.isEmpty()) {
            viewModelScope.launch {
                val wsId = workspaceId() ?: return@launch
                meshaRepository.getWorkspaceMembers(wsId).onSuccess { members ->
                    _state.update { it.copy(members = members) }
                }
            }
        }
    }

    fun dismissAssigneePicker() = _state.update { it.copy(showAssigneePicker = false) }

    fun assignTo(userId: String?) {
        _state.update { it.copy(showAssigneePicker = false, updatingAssignee = true) }
        val req = if (userId == null) UpdateIssueRequestDto(clearAssignee = true)
        else UpdateIssueRequestDto(assigneeId = userId)
        patch(req) { it.copy(updatingAssignee = false) }
    }

    // --- Labels ---

    fun showLabelEditor() {
        _state.update { it.copy(showLabelEditor = true) }
        if (_state.value.workspaceLabels.isEmpty()) {
            viewModelScope.launch {
                val wsId = workspaceId() ?: return@launch
                meshaRepository.getLabels(wsId).onSuccess { labels ->
                    _state.update { it.copy(workspaceLabels = labels) }
                }
            }
        }
    }

    fun dismissLabelEditor() = _state.update { it.copy(showLabelEditor = false, newLabelName = "") }

    fun toggleLabel(labelId: String) {
        val issue = _state.value.issue ?: return
        val current = issue.labels.map { it.id }
        val next = if (current.contains(labelId)) current - labelId else current + labelId
        _state.update { it.copy(updatingLabels = true) }
        patch(UpdateIssueRequestDto(labelIds = next)) { it.copy(updatingLabels = false) }
    }

    fun setNewLabelName(name: String) = _state.update { it.copy(newLabelName = name) }

    fun createLabel() {
        val name = _state.value.newLabelName.trim()
        if (name.isBlank()) return
        _state.update { it.copy(updatingLabels = true) }
        viewModelScope.launch {
            val wsId = workspaceId()
            if (wsId == null) {
                _state.update { it.copy(updatingLabels = false, updateError = "No workspace") }
                return@launch
            }
            meshaRepository.createLabel(wsId, name, DEFAULT_LABEL_COLOR).fold(
                onSuccess = { label ->
                    _state.update {
                        it.copy(workspaceLabels = it.workspaceLabels + label, newLabelName = "")
                    }
                    // Immediately attach the new label to the issue.
                    val issue = _state.value.issue
                    val next = (issue?.labels?.map { l -> l.id }.orEmpty() + label.id)
                    patch(UpdateIssueRequestDto(labelIds = next)) { it.copy(updatingLabels = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(updatingLabels = false, updateError = e.message) }
                },
            )
        }
    }

    // --- AI agents (definition-based) ---

    fun showAgentPicker() {
        _state.update { it.copy(showAgentPicker = true) }
        if (_state.value.activeAgents.isEmpty()) {
            viewModelScope.launch {
                val wsId = workspaceId() ?: return@launch
                meshaRepository.getActiveAgents(wsId).onSuccess { agents ->
                    _state.update { it.copy(activeAgents = agents) }
                }
            }
        }
    }

    fun dismissAgentPicker() = _state.update { it.copy(showAgentPicker = false) }

    fun assignAgent(agentDefinitionId: String) {
        _state.update { it.copy(showAgentPicker = false, updatingAgents = true) }
        viewModelScope.launch {
            meshaRepository.assignIssueAgent(projectId, issueId, agentDefinitionId).fold(
                onSuccess = { reloadAgents() },
                onFailure = { e ->
                    _state.update { it.copy(updatingAgents = false, updateError = e.message) }
                },
            )
        }
    }

    fun unassignAgent(agentDefinitionId: String) {
        _state.update { it.copy(updatingAgents = true) }
        viewModelScope.launch {
            meshaRepository.unassignIssueAgent(projectId, issueId, agentDefinitionId).fold(
                onSuccess = { reloadAgents() },
                onFailure = { e ->
                    _state.update { it.copy(updatingAgents = false, updateError = e.message) }
                },
            )
        }
    }

    private suspend fun reloadAgents() {
        val agents = meshaRepository.getIssueAgents(projectId, issueId).getOrNull().orEmpty()
        _state.update { it.copy(updatingAgents = false, issueAgents = agents) }
    }

    // --- Blocks AI sessions ---

    fun showStartSession() = _state.update { it.copy(showStartSession = true, sessionInstructions = "") }
    fun dismissStartSession() = _state.update { it.copy(showStartSession = false) }
    fun setSessionInstructions(v: String) = _state.update { it.copy(sessionInstructions = v) }

    fun startSession() {
        val instructions = _state.value.sessionInstructions.trim().takeIf { it.isNotBlank() }
        _state.update { it.copy(startingSession = true) }
        viewModelScope.launch {
            meshaRepository.startBlocksSession(projectId, issueId, instructions).fold(
                onSuccess = { reloadSessions(closeDialog = true) },
                onFailure = { e ->
                    _state.update { it.copy(startingSession = false, showStartSession = false, updateError = e.message) }
                },
            )
        }
    }

    fun cancelSession(sessionId: String) {
        viewModelScope.launch {
            meshaRepository.cancelBlocksSession(projectId, issueId, sessionId).fold(
                onSuccess = { reloadSessions(closeDialog = false) },
                onFailure = { e -> _state.update { it.copy(updateError = e.message) } },
            )
        }
    }

    private suspend fun reloadSessions(closeDialog: Boolean) {
        val sessions = meshaRepository.getBlocksSessions(projectId, issueId).getOrNull().orEmpty()
        _state.update {
            it.copy(
                startingSession = false,
                showStartSession = if (closeDialog) false else it.showStartSession,
                blocksSessions = sessions,
            )
        }
    }

    // --- Edit mode ---

    fun startEdit() {
        val issue = _state.value.issue ?: return
        _state.update {
            it.copy(
                editMode = true,
                editTitle = issue.title,
                editDescription = issue.description.orEmpty(),
                updateError = null,
            )
        }
    }

    fun cancelEdit() = _state.update { it.copy(editMode = false, updateError = null) }

    fun setEditTitle(title: String) = _state.update { it.copy(editTitle = title) }

    fun setEditDescription(description: String) = _state.update { it.copy(editDescription = description) }

    fun submitEdit() {
        val title = _state.value.editTitle.trim()
        if (title.isBlank()) {
            _state.update { it.copy(updateError = "Title cannot be empty") }
            return
        }
        _state.update { it.copy(submittingEdit = true, updateError = null) }
        patch(
            UpdateIssueRequestDto(
                title = title,
                description = _state.value.editDescription.takeIf { it.isNotBlank() },
            ),
        ) { it.copy(submittingEdit = false, editMode = false) }
    }

    // --- Delete ---

    fun showDeleteConfirm() = _state.update { it.copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() = _state.update { it.copy(showDeleteConfirm = false) }

    fun deleteIssue() {
        _state.update { it.copy(showDeleteConfirm = false, deleting = true) }
        viewModelScope.launch {
            meshaRepository.deleteIssue(projectId, issueId).fold(
                onSuccess = { _state.update { it.copy(deleting = false, deleted = true) } },
                onFailure = { e -> _state.update { it.copy(deleting = false, updateError = e.message) } },
            )
        }
    }

    fun clearUpdateError() = _state.update { it.copy(updateError = null) }

    /**
     * Apply a partial issue update, then run [after] on success. Also refreshes the activity
     * feed so the timeline reflects the change.
     */
    private fun patch(req: UpdateIssueRequestDto, after: (IssueDetailUiState) -> IssueDetailUiState) {
        viewModelScope.launch {
            meshaRepository.updateIssue(projectId, issueId, req).fold(
                onSuccess = { updated ->
                    val activity = meshaRepository.getIssueActivity(projectId, issueId).getOrNull()
                    _state.update { after(it).copy(issue = updated, activity = activity ?: it.activity) }
                },
                onFailure = { e ->
                    _state.update {
                        after(it).copy(
                            updateError = e.message,
                            updatingStatus = false,
                            updatingPriority = false,
                            updatingAssignee = false,
                            updatingLabels = false,
                            submittingEdit = false,
                        )
                    }
                },
            )
        }
    }
}
