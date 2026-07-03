package com.mesha.mobile.data.remote

import com.mesha.mobile.data.remote.dto.ActivityEventDto
import com.mesha.mobile.data.remote.dto.AgentDefinitionDto
import com.mesha.mobile.data.remote.dto.AgentSessionDto
import com.mesha.mobile.data.remote.dto.AgentSessionMessageDto
import com.mesha.mobile.data.remote.dto.AppReleaseDto
import com.mesha.mobile.data.remote.dto.AssignAgentRequestDto
import com.mesha.mobile.data.remote.dto.AssignableAgentDto
import com.mesha.mobile.data.remote.dto.AutomationRuleDto
import com.mesha.mobile.data.remote.dto.BlocksMessageDto
import com.mesha.mobile.data.remote.dto.BlocksSessionDto
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.CreateAgentDefinitionRequestDto
import com.mesha.mobile.data.remote.dto.CreateAutomationRuleRequestDto
import com.mesha.mobile.data.remote.dto.CreateCommentRequestDto
import com.mesha.mobile.data.remote.dto.CreateIssueRequestDto
import com.mesha.mobile.data.remote.dto.CreateLabelRequestDto
import com.mesha.mobile.data.remote.dto.CreateTicketRuleRequestDto
import com.mesha.mobile.data.remote.dto.IssueAgentDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.UpdateIssueRequestDto
import com.mesha.mobile.data.remote.dto.PagedResponseDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.SendMessageRequestDto
import com.mesha.mobile.data.remote.dto.StartSessionRequestDto
import com.mesha.mobile.data.remote.dto.SyncUserRequestDto
import com.mesha.mobile.data.remote.dto.TicketRuleDto
import com.mesha.mobile.data.remote.dto.UpdateAgentDefinitionRequestDto
import com.mesha.mobile.data.remote.dto.UpdateAutomationRuleRequestDto
import com.mesha.mobile.data.remote.dto.UpdateTicketRuleRequestDto
import com.mesha.mobile.data.remote.dto.WorkspaceDto
import com.mesha.mobile.data.remote.dto.WorkspaceMemberDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding to the existing Mesha REST API. Paths mirror the backend
 * controllers exactly — the mobile app introduces no mobile-specific endpoints
 * beyond the (shared) public release endpoints used for update checks.
 */
interface MeshaApi {

    // --- Auth: sync the Clerk user into Mesha after login ---
    @POST("api/auth/sync")
    suspend fun syncUser(@Body body: SyncUserRequestDto): Unit

    // --- Workspaces ---
    @GET("api/workspaces")
    suspend fun getWorkspaces(): List<WorkspaceDto>

    @GET("api/workspaces/{workspaceId}/members")
    suspend fun getWorkspaceMembers(
        @Path("workspaceId") workspaceId: String,
    ): List<WorkspaceMemberDto>

    // --- Projects ---
    @GET("api/workspaces/{workspaceId}/projects")
    suspend fun getProjects(@Path("workspaceId") workspaceId: String): List<ProjectDto>

    // --- Labels (workspace-scoped) ---
    @GET("api/workspaces/{workspaceId}/labels")
    suspend fun getLabels(@Path("workspaceId") workspaceId: String): List<LabelDto>

    @POST("api/workspaces/{workspaceId}/labels")
    suspend fun createLabel(
        @Path("workspaceId") workspaceId: String,
        @Body body: CreateLabelRequestDto,
    ): LabelDto

    // --- Project statuses (custom, per-project workflow stages) ---
    @GET("api/projects/{projectId}/statuses")
    suspend fun getProjectStatuses(
        @Path("projectId") projectId: String,
    ): List<ProjectStatusDto>

    // --- Issues ---
    @GET("api/projects/{projectId}/issues")
    suspend fun getIssues(
        @Path("projectId") projectId: String,
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("assigneeId") assigneeId: String? = null,
        @Query("search") search: String? = null,
        @Query("labelIds") labelIds: List<String>? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 25,
    ): PagedResponseDto<IssueDto>

    @GET("api/projects/{projectId}/issues/{issueId}")
    suspend fun getIssue(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
    ): IssueDto

    @POST("api/projects/{projectId}/issues")
    suspend fun createIssue(
        @Path("projectId") projectId: String,
        @Body body: CreateIssueRequestDto,
    ): IssueDto

    @PATCH("api/projects/{projectId}/issues/{issueId}")
    suspend fun updateIssue(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
        @Body body: UpdateIssueRequestDto,
    ): IssueDto

    @DELETE("api/projects/{projectId}/issues/{issueId}")
    suspend fun deleteIssue(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
    ): Unit

    @GET("api/projects/{projectId}/issues/{issueId}/activity")
    suspend fun getIssueActivity(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
    ): List<ActivityEventDto>

    // --- Issue AI agents (definition-based assignment) ---
    @GET("api/projects/{projectId}/issues/{issueId}/agents")
    suspend fun getIssueAgents(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
    ): List<IssueAgentDto>

    @POST("api/projects/{projectId}/issues/{issueId}/agents")
    suspend fun assignIssueAgent(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
        @Body body: AssignAgentRequestDto,
    ): IssueAgentDto

    @DELETE("api/projects/{projectId}/issues/{issueId}/agents/{agentDefinitionId}")
    suspend fun unassignIssueAgent(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
        @Path("agentDefinitionId") agentDefinitionId: String,
    ): Unit

    // --- Blocks AI sessions (provider-managed, issue-scoped) ---
    @GET("api/projects/{projectId}/issues/{issueId}/blocks-sessions")
    suspend fun getBlocksSessions(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
    ): List<BlocksSessionDto>

    @POST("api/projects/{projectId}/issues/{issueId}/blocks-sessions")
    suspend fun startBlocksSession(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
        @Body body: StartSessionRequestDto,
    ): BlocksSessionDto

    @POST("api/projects/{projectId}/issues/{issueId}/blocks-sessions/{sessionId}/cancel")
    suspend fun cancelBlocksSession(
        @Path("projectId") projectId: String,
        @Path("issueId") issueId: String,
        @Path("sessionId") sessionId: String,
    ): BlocksSessionDto

    // --- Comments ---
    @GET("api/issues/{issueId}/comments")
    suspend fun getComments(@Path("issueId") issueId: String): List<CommentDto>

    @POST("api/issues/{issueId}/comments")
    suspend fun addComment(
        @Path("issueId") issueId: String,
        @Body body: CreateCommentRequestDto,
    ): CommentDto

    @DELETE("api/issues/{issueId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("issueId") issueId: String,
        @Path("commentId") commentId: String,
    ): Unit

    // --- Agents (assignable agents = definitions + connector agents) ---
    @GET("api/workspaces/{workspaceId}/agents/active")
    suspend fun getActiveAgents(@Path("workspaceId") workspaceId: String): List<AssignableAgentDto>

    // --- Agent definitions (workspace-scoped custom AI agent config) ---
    @GET("api/workspaces/{workspaceId}/agents")
    suspend fun getAgentDefinitions(
        @Path("workspaceId") workspaceId: String,
    ): List<AgentDefinitionDto>

    @POST("api/workspaces/{workspaceId}/agents")
    suspend fun createAgentDefinition(
        @Path("workspaceId") workspaceId: String,
        @Body body: CreateAgentDefinitionRequestDto,
    ): AgentDefinitionDto

    @PUT("api/workspaces/{workspaceId}/agents/{agentId}")
    suspend fun updateAgentDefinition(
        @Path("workspaceId") workspaceId: String,
        @Path("agentId") agentId: String,
        @Body body: UpdateAgentDefinitionRequestDto,
    ): AgentDefinitionDto

    @DELETE("api/workspaces/{workspaceId}/agents/{agentId}")
    suspend fun deleteAgentDefinition(
        @Path("workspaceId") workspaceId: String,
        @Path("agentId") agentId: String,
    ): Unit

    // --- Automation rules (project-scoped: trigger -> actions) ---
    @GET("api/projects/{projectId}/automations")
    suspend fun getAutomationRules(
        @Path("projectId") projectId: String,
    ): List<AutomationRuleDto>

    @POST("api/projects/{projectId}/automations")
    suspend fun createAutomationRule(
        @Path("projectId") projectId: String,
        @Body body: CreateAutomationRuleRequestDto,
    ): AutomationRuleDto

    @PATCH("api/projects/{projectId}/automations/{ruleId}")
    suspend fun updateAutomationRule(
        @Path("projectId") projectId: String,
        @Path("ruleId") ruleId: String,
        @Body body: UpdateAutomationRuleRequestDto,
    ): AutomationRuleDto

    @DELETE("api/projects/{projectId}/automations/{ruleId}")
    suspend fun deleteAutomationRule(
        @Path("projectId") projectId: String,
        @Path("ruleId") ruleId: String,
    ): Unit

    // --- Ticket rules (project-scoped guardrails: conditions -> restrictions) ---
    @GET("api/projects/{projectId}/ticket-rules")
    suspend fun getTicketRules(
        @Path("projectId") projectId: String,
    ): List<TicketRuleDto>

    @POST("api/projects/{projectId}/ticket-rules")
    suspend fun createTicketRule(
        @Path("projectId") projectId: String,
        @Body body: CreateTicketRuleRequestDto,
    ): TicketRuleDto

    @PATCH("api/projects/{projectId}/ticket-rules/{ruleId}")
    suspend fun updateTicketRule(
        @Path("projectId") projectId: String,
        @Path("ruleId") ruleId: String,
        @Body body: UpdateTicketRuleRequestDto,
    ): TicketRuleDto

    @DELETE("api/projects/{projectId}/ticket-rules/{ruleId}")
    suspend fun deleteTicketRule(
        @Path("projectId") projectId: String,
        @Path("ruleId") ruleId: String,
    ): Unit

    // --- Sessions (connector agent sessions) ---
    @GET("api/agent-sessions")
    suspend fun getSessions(): List<AgentSessionDto>

    @GET("api/agent-sessions/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: String): AgentSessionDto

    @GET("api/agent-sessions/{sessionId}/messages")
    suspend fun getSessionMessages(
        @Path("sessionId") sessionId: String,
    ): List<AgentSessionMessageDto>

    @POST("api/agent-sessions/{sessionId}/messages")
    suspend fun sendSessionMessage(
        @Path("sessionId") sessionId: String,
        @Body body: SendMessageRequestDto,
    ): AgentSessionMessageDto

    // --- Releases (public — drives the in-app update check) ---
    @GET("api/releases/{platform}/latest")
    suspend fun getLatestRelease(@Path("platform") platform: String = "android"): AppReleaseDto
}
