package com.mesha.mobile.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.IssueDto
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
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IssueDetailUiState())
    val state: StateFlow<IssueDetailUiState> = _state.asStateFlow()

    fun load(projectId: String, issueId: String) {
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
}
