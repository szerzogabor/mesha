package com.mesha.mobile.data.remote

import com.mesha.mobile.data.remote.dto.AgentSessionDto
import com.mesha.mobile.data.remote.dto.AgentSessionMessageDto
import com.mesha.mobile.data.remote.dto.AppReleaseDto
import com.mesha.mobile.data.remote.dto.AssignableAgentDto
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.CreateCommentRequestDto
import com.mesha.mobile.data.remote.dto.CreateIssueRequestDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.PagedResponseDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.SendMessageRequestDto
import com.mesha.mobile.data.remote.dto.SyncUserRequestDto
import com.mesha.mobile.data.remote.dto.WorkspaceDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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

    // --- Projects ---
    @GET("api/workspaces/{workspaceId}/projects")
    suspend fun getProjects(@Path("workspaceId") workspaceId: String): List<ProjectDto>

    // --- Labels (workspace-scoped) ---
    @GET("api/workspaces/{workspaceId}/labels")
    suspend fun getLabels(@Path("workspaceId") workspaceId: String): List<LabelDto>

    // --- Issues ---
    @GET("api/projects/{projectId}/issues")
    suspend fun getIssues(
        @Path("projectId") projectId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
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

    // --- Comments ---
    @GET("api/issues/{issueId}/comments")
    suspend fun getComments(@Path("issueId") issueId: String): List<CommentDto>

    @POST("api/issues/{issueId}/comments")
    suspend fun addComment(
        @Path("issueId") issueId: String,
        @Body body: CreateCommentRequestDto,
    ): CommentDto

    // --- Agents (assignable agents = definitions + connector agents) ---
    @GET("api/workspaces/{workspaceId}/agents/active")
    suspend fun getActiveAgents(@Path("workspaceId") workspaceId: String): List<AssignableAgentDto>

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
