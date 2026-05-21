package com.mesha.api.dto;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.BlocksSession;

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
    Instant createdAt,
    Instant updatedAt
) {
    public static BlocksSessionDto from(BlocksSession s) {
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
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }
}
