package com.mesha.mobile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** Spring `PagedResponse<T>` mirror. */
@Serializable
data class PagedResponseDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val last: Boolean = true,
)

@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    val slug: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class ProjectDto(
    val id: String,
    val workspaceId: String,
    val name: String,
    val description: String? = null,
    val key: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String? = null,
    val name: String? = null,
)

@Serializable
data class LabelDto(
    val id: String,
    val workspaceId: String? = null,
    val name: String,
    val color: String? = null,
)

@Serializable
data class IssueDto(
    val id: String,
    val projectId: String,
    val identifier: String? = null,
    val title: String,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val assignee: UserDto? = null,
    val labels: List<LabelDto> = emptyList(),
    val aiAssignmentState: String? = null,
    val agentType: String? = null,
    val agentLlm: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastPullRequest: GitHubPullRequestDto? = null,
)

/** Mirrors backend `GitHubPullRequestDto` — only the fields the mobile UI renders. */
@Serializable
data class GitHubPullRequestDto(
    val id: String,
    val githubPrNumber: Int? = null,
    val title: String? = null,
    val state: String? = null,
    val htmlUrl: String? = null,
    val sourceBranch: String? = null,
    val targetBranch: String? = null,
    val draft: Boolean? = null,
    val reviewState: String? = null,
    val checksStatus: String? = null,
    val linkedSessionId: String? = null,
)

/** A file attached to an issue (mirrors backend `IssueAttachmentDto`). */
@Serializable
data class IssueAttachmentDto(
    val id: String,
    val issueId: String? = null,
    val fileName: String,
    val contentType: String? = null,
    val fileSize: Long = 0,
    val uploadedByName: String? = null,
    val createdAt: String? = null,
)

/** Per-project custom workflow status (mirrors backend `ProjectStatusDto`). */
@Serializable
data class ProjectStatusDto(
    val id: String,
    val projectId: String? = null,
    val name: String,
    val color: String? = null,
    val position: Int? = null,
    val createdAt: String? = null,
)

/** Workspace member (mirrors backend `WorkspaceMemberDto`) — used for the assignee picker. */
@Serializable
data class WorkspaceMemberDto(
    val id: String,
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val role: String? = null,
)

/** An AI agent assigned to an issue (mirrors backend `IssueAgentDto`). */
@Serializable
data class IssueAgentDto(
    val id: String,
    val issueId: String? = null,
    val agentDefinitionId: String,
    val agentTitle: String? = null,
    val agentName: String? = null,
    val providerType: String? = null,
    val agentActive: Boolean = false,
    val assignedAt: String? = null,
    val assignedBy: String? = null,
)

@Serializable
data class AssignAgentRequestDto(val agentDefinitionId: String)

@Serializable
data class CreateLabelRequestDto(
    val name: String,
    val color: String? = null,
)

/** Issue activity timeline entry (mirrors backend `ActivityEventDto`). */
@Serializable
data class ActivityEventDto(
    val id: String,
    val issueId: String? = null,
    val user: UserDto? = null,
    val eventType: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAt: String? = null,
)

/** A Blocks (provider-managed) AI session on an issue (mirrors backend `BlocksSessionDto`). */
@Serializable
data class BlocksSessionDto(
    val id: String,
    val issueId: String? = null,
    val provider: String? = null,
    val providerSessionId: String? = null,
    val executionState: String? = null,
    val retryCount: Int = 0,
    val prUrl: String? = null,
    val prNumber: Int? = null,
    val branchName: String? = null,
    val errorMessage: String? = null,
    val sessionUrl: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val linkedPullRequests: List<GitHubPullRequestDto> = emptyList(),
)

@Serializable
data class BlocksMessageDto(
    val id: String,
    val sessionId: String? = null,
    val message: String,
    val role: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class StartSessionRequestDto(val instructions: String? = null)

@Serializable
data class CreateIssueRequestDto(
    val title: String,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val assigneeId: String? = null,
    val labelIds: List<String>? = null,
    val agentType: String? = null,
    val agentLlm: String? = null,
)

@Serializable
data class CommentDto(
    val id: String,
    val issueId: String,
    val body: String,
    val author: UserDto? = null,
    val parentId: String? = null,
    val replies: List<CommentDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateCommentRequestDto(
    val body: String,
    val parentId: String? = null,
)

/** Unified assignable agent (agent definition or connector agent). `active` => online. */
@Serializable
data class AssignableAgentDto(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val providerType: String? = null,
    val active: Boolean = false,
)

@Serializable
data class AgentSessionDto(
    val id: String,
    val agentId: String? = null,
    val issueId: String? = null,
    val issueIdentifier: String? = null,
    val issueTitle: String? = null,
    val status: String? = null,
    val instructions: String? = null,
    val errorMessage: String? = null,
    val branchName: String? = null,
    val prUrl: String? = null,
    val prNumber: Int? = null,
    val prTitle: String? = null,
    val queuedAt: String? = null,
    val claimedAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class AgentSessionMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: String? = null,
)

@Serializable
data class SyncUserRequestDto(
    val email: String,
    val name: String? = null,
)

@Serializable
data class SendMessageRequestDto(val content: String)

@Serializable
data class UpdateIssueRequestDto(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val assigneeId: String? = null,
    val clearAssignee: Boolean? = null,
    val labelIds: List<String>? = null,
    val agentType: String? = null,
    val agentLlm: String? = null,
    val clearAgentAssignee: Boolean? = null,
)

// --- Agent definitions (workspace-scoped, custom AI agent config) ---

/** Mirrors backend `AgentDefinitionDto`. `providerParameters` is a free-form JSON object. */
@Serializable
data class AgentDefinitionDto(
    val id: String,
    val workspaceId: String? = null,
    val name: String,
    val title: String,
    val description: String? = null,
    val providerType: String,
    val systemPrompt: String? = null,
    val providerParameters: JsonObject? = null,
    val blocksAgentName: String? = null,
    val active: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    /** The one provider parameter the app manages: startup commands run when a session begins. */
    val startupCommands: List<String>
        get() = providerParameters.startupCommands()
}

@Serializable
data class CreateAgentDefinitionRequestDto(
    val title: String,
    val name: String,
    val description: String? = null,
    val providerType: String,
    val systemPrompt: String,
    val providerParameters: JsonObject? = null,
    val blocksAgentName: String? = null,
    val active: Boolean? = null,
)

@Serializable
data class UpdateAgentDefinitionRequestDto(
    val title: String? = null,
    val name: String? = null,
    val description: String? = null,
    val providerType: String? = null,
    val systemPrompt: String? = null,
    val providerParameters: JsonObject? = null,
    val blocksAgentName: String? = null,
    val active: Boolean? = null,
)

/**
 * Read `startupCommands` (a `List<String>`) out of a provider-parameters JSON object.
 * Uses safe casts so an unexpected shape (e.g. `null` or a non-array value) yields an
 * empty list instead of throwing.
 */
fun JsonObject?.startupCommands(): List<String> =
    (this?.get("startupCommands") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?: emptyList()

/**
 * Return a copy of this provider-parameters object with `startupCommands` set to [commands],
 * preserving any other keys the server may carry (called on `null` for a fresh object).
 */
fun JsonObject?.withStartupCommands(commands: List<String>): JsonObject = buildJsonObject {
    this@withStartupCommands?.forEach { (k, v) -> if (k != "startupCommands") put(k, v) }
    put("startupCommands", JsonArray(commands.map { JsonPrimitive(it) }))
}

// --- Automation rules (project-scoped: trigger -> actions) ---

@Serializable
data class AutomationRuleDto(
    val id: String,
    val projectId: String? = null,
    val triggerType: String,
    val triggerValue: String? = null,
    val actions: List<AutomationActionDto> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class AutomationActionDto(
    val actionType: String,
    val actionValue: String? = null,
    val conditions: List<AutomationActionConditionDto> = emptyList(),
)

@Serializable
data class AutomationActionConditionDto(
    val conditionType: String,
    val conditionValue: String? = null,
)

@Serializable
data class CreateAutomationRuleRequestDto(
    val triggerType: String,
    val triggerValue: String? = null,
    val actions: List<AutomationActionRequestDto>,
)

@Serializable
data class AutomationActionRequestDto(
    val actionType: String,
    val actionValue: String? = null,
    val conditions: List<AutomationActionConditionRequestDto>? = null,
)

@Serializable
data class AutomationActionConditionRequestDto(
    val conditionType: String,
    val conditionValue: String? = null,
)

@Serializable
data class UpdateAutomationRuleRequestDto(
    val triggerType: String? = null,
    val triggerValue: String? = null,
    val actions: List<AutomationActionRequestDto>? = null,
    val enabled: Boolean? = null,
)

// --- Ticket rules (project-scoped guardrails: conditions -> restrictions) ---

@Serializable
data class TicketRuleDto(
    val id: String,
    val projectId: String? = null,
    val name: String,
    val enabled: Boolean = true,
    val conditions: List<TicketRuleConditionDto> = emptyList(),
    val restrictions: List<TicketRuleRestrictionDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class TicketRuleConditionDto(
    val id: String? = null,
    val conditionType: String,
    val conditionValue: String? = null,
    val position: Int = 0,
)

@Serializable
data class TicketRuleRestrictionDto(
    val id: String? = null,
    val restrictionType: String,
    val restrictionValue: String? = null,
    val position: Int = 0,
)

@Serializable
data class CreateTicketRuleRequestDto(
    val name: String,
    val conditions: List<TicketRuleConditionRequestDto>,
    val restrictions: List<TicketRuleRestrictionRequestDto>,
)

@Serializable
data class TicketRuleConditionRequestDto(
    val conditionType: String,
    val conditionValue: String? = null,
)

@Serializable
data class TicketRuleRestrictionRequestDto(
    val restrictionType: String,
    val restrictionValue: String? = null,
)

@Serializable
data class UpdateTicketRuleRequestDto(
    val name: String? = null,
    val enabled: Boolean? = null,
    val conditions: List<TicketRuleConditionRequestDto>? = null,
    val restrictions: List<TicketRuleRestrictionRequestDto>? = null,
)

/** Mirrors backend `AppReleaseDto` for the in-app update check. */
@Serializable
data class AppReleaseDto(
    val id: String,
    val platform: String,
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String? = null,
    val minSdk: Int = 33,
    val fileName: String,
    val fileSize: Long,
    val checksumSha256: String,
    val published: Boolean = true,
    val downloadUrl: String,
    val createdAt: String? = null,
)
