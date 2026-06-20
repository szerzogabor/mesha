package com.mesha.api.dto;

import com.mesha.api.model.ConnectorAgentSession;
import com.mesha.api.model.ConnectorAgentSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record ConnectorAgentSessionDto(
    UUID id,
    UUID agentId,
    UUID issueId,
    ConnectorAgentSessionStatus status,
    String instructions,
    String errorMessage,
    String branchName,
    String workspacePath,
    Instant queuedAt,
    Instant claimedAt,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static ConnectorAgentSessionDto from(ConnectorAgentSession s) {
        return new ConnectorAgentSessionDto(
            s.getId(),
            s.getAgentId(),
            s.getIssueId(),
            s.getStatus(),
            s.getInstructions(),
            s.getErrorMessage(),
            s.getBranchName(),
            s.getWorkspacePath(),
            s.getQueuedAt(),
            s.getClaimedAt(),
            s.getStartedAt(),
            s.getCompletedAt(),
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }
}
