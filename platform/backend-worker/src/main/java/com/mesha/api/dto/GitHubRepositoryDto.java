package com.mesha.api.dto;

import com.mesha.api.model.GitHubRepository;
import java.time.Instant;
import java.util.UUID;

public record GitHubRepositoryDto(
        UUID id,
        UUID workspaceId,
        Long githubRepoId,
        String owner,
        String name,
        String fullName,
        Boolean isPrivate,
        String defaultBranch,
        String description,
        String htmlUrl,
        Boolean connected,
        Instant lastSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static GitHubRepositoryDto from(GitHubRepository r) {
        return new GitHubRepositoryDto(
                r.getId(),
                r.getWorkspace().getId(),
                r.getGithubRepoId(),
                r.getOwner(),
                r.getName(),
                r.getFullName(),
                r.getIsPrivate(),
                r.getDefaultBranch(),
                r.getDescription(),
                r.getHtmlUrl(),
                r.getConnected(),
                r.getLastSyncedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
