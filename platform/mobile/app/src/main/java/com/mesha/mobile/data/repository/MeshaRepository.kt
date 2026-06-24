package com.mesha.mobile.data.repository

import com.mesha.mobile.data.remote.MeshaApi
import com.mesha.mobile.data.remote.dto.AgentSessionDto
import com.mesha.mobile.data.remote.dto.AgentSessionMessageDto
import com.mesha.mobile.data.remote.dto.AssignableAgentDto
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.CreateCommentRequestDto
import com.mesha.mobile.data.remote.dto.CreateIssueRequestDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.SendMessageRequestDto
import com.mesha.mobile.data.remote.dto.WorkspaceDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/write access to the core Mesha domain over the existing REST API. All calls are
 * wrapped in [Result] so ViewModels can render error states without try/catch noise, and
 * are dispatched on IO.
 */
@Singleton
class MeshaRepository @Inject constructor(
    private val api: MeshaApi,
) {
    suspend fun getWorkspaces(): Result<List<WorkspaceDto>> =
        io { api.getWorkspaces() }

    suspend fun getProjects(workspaceId: String): Result<List<ProjectDto>> =
        io { api.getProjects(workspaceId) }

    suspend fun getLabels(workspaceId: String): Result<List<LabelDto>> =
        io { api.getLabels(workspaceId) }

    suspend fun getIssues(projectId: String, page: Int = 0, size: Int = 50): Result<List<IssueDto>> =
        io { api.getIssues(projectId, page, size).content }

    suspend fun getIssue(projectId: String, issueId: String): Result<IssueDto> =
        io { api.getIssue(projectId, issueId) }

    suspend fun createIssue(projectId: String, body: CreateIssueRequestDto): Result<IssueDto> =
        io { api.createIssue(projectId, body) }

    suspend fun getComments(issueId: String): Result<List<CommentDto>> =
        io { api.getComments(issueId) }

    suspend fun addComment(issueId: String, body: String, parentId: String? = null): Result<CommentDto> =
        io { api.addComment(issueId, CreateCommentRequestDto(body, parentId)) }

    suspend fun getActiveAgents(workspaceId: String): Result<List<AssignableAgentDto>> =
        io { api.getActiveAgents(workspaceId) }

    suspend fun getSessions(): Result<List<AgentSessionDto>> =
        io { api.getSessions() }

    suspend fun getSession(sessionId: String): Result<AgentSessionDto> =
        io { api.getSession(sessionId) }

    suspend fun getSessionMessages(sessionId: String): Result<List<AgentSessionMessageDto>> =
        io { api.getSessionMessages(sessionId) }

    suspend fun sendSessionMessage(sessionId: String, content: String): Result<AgentSessionMessageDto> =
        io { api.sendSessionMessage(sessionId, SendMessageRequestDto(content)) }

    private suspend fun <T> io(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }
}
