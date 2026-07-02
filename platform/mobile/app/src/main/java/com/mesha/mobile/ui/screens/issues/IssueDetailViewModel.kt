package com.mesha.mobile.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.UpdateIssueRequestDto
import com.mesha.mobile.data.repository.MeshaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IssueDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val issue: IssueDto? = null,
    val comments: List<CommentDto> = emptyList(),
    // Comment input
    val commentInput: String = "",
    val sendingComment: Boolean = false,
    // Status picker
    val showStatusPicker: Boolean = false,
    val updatingStatus: Boolean = false,
    // Priority picker
    val showPriorityPicker: Boolean = false,
    val updatingPriority: Boolean = false,
    // Inline edit mode
    val editMode: Boolean = false,
    val editTitle: String = "",
    val editDescription: String = "",
    val submittingEdit: Boolean = false,
    val updateError: String? = null,
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
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
            if (issue == null) {
                _state.update { it.copy(loading = false, error = "Issue not found") }
            } else {
                _state.update { it.copy(loading = false, issue = issue, comments = comments) }
            }
        }
    }

    // --- Comment ---

    fun setCommentInput(text: String) {
        _state.update { it.copy(commentInput = text) }
    }

    fun sendComment() {
        val body = _state.value.commentInput.trim()
        if (body.isBlank() || issueId.isBlank()) return
        _state.update { it.copy(sendingComment = true) }
        viewModelScope.launch {
            meshaRepository.addComment(issueId, body).fold(
                onSuccess = { comment ->
                    _state.update {
                        it.copy(
                            sendingComment = false,
                            commentInput = "",
                            comments = it.comments + comment,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(sendingComment = false, updateError = e.message) }
                },
            )
        }
    }

    // --- Status ---

    fun showStatusPicker() = _state.update { it.copy(showStatusPicker = true) }
    fun dismissStatusPicker() = _state.update { it.copy(showStatusPicker = false) }

    fun updateStatus(status: String) {
        _state.update { it.copy(showStatusPicker = false, updatingStatus = true) }
        viewModelScope.launch {
            meshaRepository.updateIssue(projectId, issueId, UpdateIssueRequestDto(status = status)).fold(
                onSuccess = { updated ->
                    _state.update { it.copy(updatingStatus = false, issue = updated) }
                },
                onFailure = { e ->
                    _state.update { it.copy(updatingStatus = false, updateError = e.message) }
                },
            )
        }
    }

    // --- Priority ---

    fun showPriorityPicker() = _state.update { it.copy(showPriorityPicker = true) }
    fun dismissPriorityPicker() = _state.update { it.copy(showPriorityPicker = false) }

    fun updatePriority(priority: String) {
        _state.update { it.copy(showPriorityPicker = false, updatingPriority = true) }
        viewModelScope.launch {
            meshaRepository.updateIssue(projectId, issueId, UpdateIssueRequestDto(priority = priority)).fold(
                onSuccess = { updated ->
                    _state.update { it.copy(updatingPriority = false, issue = updated) }
                },
                onFailure = { e ->
                    _state.update { it.copy(updatingPriority = false, updateError = e.message) }
                },
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
        viewModelScope.launch {
            meshaRepository.updateIssue(
                projectId,
                issueId,
                UpdateIssueRequestDto(
                    title = title,
                    description = _state.value.editDescription.takeIf { it.isNotBlank() },
                ),
            ).fold(
                onSuccess = { updated ->
                    _state.update { it.copy(submittingEdit = false, editMode = false, issue = updated) }
                },
                onFailure = { e ->
                    _state.update { it.copy(submittingEdit = false, updateError = e.message) }
                },
            )
        }
    }

    fun clearUpdateError() = _state.update { it.copy(updateError = null) }
}
