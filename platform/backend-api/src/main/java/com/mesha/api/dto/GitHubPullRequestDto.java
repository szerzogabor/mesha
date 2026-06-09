package com.mesha.api.dto;

import com.mesha.api.model.GitHubPullRequest;
import java.time.Instant;
import java.util.UUID;

public record GitHubPullRequestDto(
        UUID id,
        UUID repositoryId,
        Integer githubPrNumber,
        String title,
        String body,
        String state,
        String authorLogin,
        String authorAvatarUrl,
        String sourceBranch,
        String targetBranch,
        String htmlUrl,
        Boolean draft,
        Integer commitsCount,
        String reviewState,
        String checksStatus,
        Instant mergedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID linkedSessionId
) {
    public static GitHubPullRequestDto from(GitHubPullRequest pr) {
        return new GitHubPullRequestDto(
                pr.getId(),
                pr.getRepository().getId(),
                pr.getGithubPrNumber(),
                pr.getTitle(),
                pr.getBody(),
                pr.getState(),
                pr.getAuthorLogin(),
                pr.getAuthorAvatarUrl(),
                pr.getSourceBranch(),
                pr.getTargetBranch(),
                pr.getHtmlUrl(),
                pr.getDraft(),
                pr.getCommitsCount(),
                pr.getReviewState(),
                pr.getChecksStatus(),
                pr.getMergedAt(),
                pr.getClosedAt(),
                pr.getCreatedAt(),
                pr.getUpdatedAt(),
                pr.getBlocksSession() != null ? pr.getBlocksSession().getId() : null
        );
    }
}
