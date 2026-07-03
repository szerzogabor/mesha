package com.mesha.mobile.data.repository

import com.mesha.mobile.data.remote.MeshaApi
import com.mesha.mobile.data.remote.dto.ActivityEventDto
import com.mesha.mobile.data.remote.dto.AgentSessionDto
import com.mesha.mobile.data.remote.dto.AgentSessionMessageDto
import com.mesha.mobile.data.remote.dto.AssignAgentRequestDto
import com.mesha.mobile.data.remote.dto.AssignableAgentDto
import com.mesha.mobile.data.remote.dto.BlocksSessionDto
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.CreateCommentRequestDto
import com.mesha.mobile.data.remote.dto.CreateIssueRequestDto
import com.mesha.mobile.data.remote.dto.CreateLabelRequestDto
import com.mesha.mobile.data.remote.dto.IssueAgentDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.PagedResponseDto
import com.mesha.mobile.data.remote.dto.StartSessionRequestDto
import com.mesha.mobile.data.remote.dto.UpdateIssueRequestDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.SendMessageRequestDto
import com.mesha.mobile.data.remote.dto.WorkspaceDto
import com.mesha.mobile.data.remote.dto.WorkspaceMemberDto
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

    suspend fun createLabel(workspaceId: String, name: String, color: String?): Result<LabelDto> =
        io { api.createLabel(workspaceId, CreateLabelRequestDto(name, color)) }

    suspend fun getWorkspaceMembers(workspaceId: String): Result<List<WorkspaceMemberDto>> =
        io { api.getWorkspaceMembers(workspaceId) }

    suspend fun getProjectStatuses(projectId: String): Result<List<ProjectStatusDto>> =
        io { api.getProjectStatuses(projectId) }

    suspend fun getIssuesPaged(
        projectId: String,
        status: String? = null,
        priority: String? = null,
        assigneeId: String? = null,
        search: String? = null,
        labelIds: List<String>? = null,
        page: Int = 0,
        size: Int = 25,
    ): Result<PagedResponseDto<IssueDto>> =
        io { api.getIssues(projectId, status, priority, assigneeId, search, labelIds, page, size) }

    suspend fun getIssues(projectId: String, page: Int = 0, size: Int = 50): Result<List<IssueDto>> =
        io { api.getIssues(projectId, page = page, size = size).content }

    suspend fun getIssue(projectId: String, issueId: String): Result<IssueDto> =
        io { api.getIssue(projectId, issueId) }

    suspend fun createIssue(projectId: String, body: CreateIssueRequestDto): Result<IssueDto> =
        io { api.createIssue(projectId, body) }

    suspend fun updateIssue(projectId: String, issueId: String, body: UpdateIssueRequestDto): Result<IssueDto> =
        io { api.updateIssue(projectId, issueId, body) }

    suspend fun deleteIssue(projectId: String, issueId: String): Result<Unit> =
        io { api.deleteIssue(projectId, issueId) }

    suspend fun getIssueActivity(projectId: String, issueId: String): Result<List<ActivityEventDto>> =
        io { api.getIssueActivity(projectId, issueId) }

    // --- Issue AI agents ---

    suspend fun getIssueAgents(projectId: String, issueId: String): Result<List<IssueAgentDto>> =
        io { api.getIssueAgents(projectId, issueId) }

    suspend fun assignIssueAgent(projectId: String, issueId: String, agentDefinitionId: String): Result<IssueAgentDto> =
        io { api.assignIssueAgent(projectId, issueId, AssignAgentRequestDto(agentDefinitionId)) }

    suspend fun unassignIssueAgent(projectId: String, issueId: String, agentDefinitionId: String): Result<Unit> =
        io { api.unassignIssueAgent(projectId, issueId, agentDefinitionId) }

    // --- Blocks AI sessions ---

    suspend fun getBlocksSessions(projectId: String, issueId: String): Result<List<BlocksSessionDto>> =
        io { api.getBlocksSessions(projectId, issueId) }

    suspend fun startBlocksSession(projectId: String, issueId: String, instructions: String?): Result<BlocksSessionDto> =
        io { api.startBlocksSession(projectId, issueId, StartSessionRequestDto(instructions)) }

    suspend fun cancelBlocksSession(projectId: String, issueId: String, sessionId: String): Result<BlocksSessionDto> =
        io { api.cancelBlocksSession(projectId, issueId, sessionId) }

    suspend fun getComments(issueId: String): Result<List<CommentDto>> =
        io { api.getComments(issueId) }

    suspend fun addComment(issueId: String, body: String, parentId: String? = null): Result<CommentDto> =
        io { api.addComment(issueId, CreateCommentRequestDto(body, parentId)) }

    suspend fun deleteComment(issueId: String, commentId: String): Result<Unit> =
        io { api.deleteComment(issueId, commentId) }

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
