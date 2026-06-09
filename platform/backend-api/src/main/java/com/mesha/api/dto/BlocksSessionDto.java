package com.mesha.api.dto;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubPullRequest;

import java.time.Instant;
import java.util.UUID;

public record BlocksSessionDto(
    UUID id,
    UUID issueId,
    String provider,
    String providerSessionId,
    AIExecutionState executionState,
    int retryCount,
    String prUrl,
    Integer prNumber,
    String branchName,
    String errorMessage,
    String sessionUrl,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    GitHubPullRequestDto linkedPullRequest
) {
    public static BlocksSessionDto from(BlocksSession s) {
        return from(s, null);
    }

    public static BlocksSessionDto from(BlocksSession s, GitHubPullRequest linkedPr) {
        return new BlocksSessionDto(
            s.getId(),
            s.getIssue().getId(),
            s.getProvider(),
            s.getProviderSessionId(),
            s.getExecutionState(),
            s.getRetryCount(),
            s.getPrUrl(),
            s.getPrNumber(),
            s.getBranchName(),
            s.getErrorMessage(),
            s.getSessionUrl(),
            s.getStartedAt(),
            s.getCompletedAt(),
            s.getCreatedAt(),
            s.getUpdatedAt(),
            linkedPr != null ? GitHubPullRequestDto.from(linkedPr) : null
        );
    }
}
