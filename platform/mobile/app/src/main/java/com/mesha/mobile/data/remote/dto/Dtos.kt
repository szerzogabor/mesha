package com.mesha.mobile.data.remote.dto

import kotlinx.serialization.Serializable

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
)

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
    val labelIds: List<String>? = null,
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
