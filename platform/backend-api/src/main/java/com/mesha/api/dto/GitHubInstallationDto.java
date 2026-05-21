package com.mesha.api.dto;

import com.mesha.api.model.GitHubInstallation;
import java.time.Instant;
import java.util.UUID;

public record GitHubInstallationDto(
        UUID id,
        Long installationId,
        Long appId,
        String accountLogin,
        String accountType,
        String accountAvatarUrl,
        String status,
        Instant createdAt
) {
    public static GitHubInstallationDto from(GitHubInstallation i) {
        return new GitHubInstallationDto(
                i.getId(),
                i.getInstallationId(),
                i.getAppId(),
                i.getAccountLogin(),
                i.getAccountType(),
                i.getAccountAvatarUrl(),
                i.getStatus(),
                i.getCreatedAt()
        );
    }
}
