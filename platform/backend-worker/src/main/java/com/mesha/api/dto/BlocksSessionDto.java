package com.mesha.api.dto;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubPullRequest;

import java.time.Instant;
import java.util.List;
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
    List<GitHubPullRequestDto> linkedPullRequests
) {
    public static BlocksSessionDto from(BlocksSession s) {
        return from(s, List.of());
    }

    public static BlocksSessionDto from(BlocksSession s, List<GitHubPullRequest> linkedPrs) {
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
            linkedPrs.stream().map(GitHubPullRequestDto::from).toList()
        );
    }
}
