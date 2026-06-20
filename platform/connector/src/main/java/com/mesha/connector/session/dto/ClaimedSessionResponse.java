package com.mesha.connector.session.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors backend-api's {@code ConnectorAgentSessionDto}. */
public record ClaimedSessionResponse(
        UUID id,
        UUID agentId,
        UUID issueId,
        String status,
        String instructions,
        String errorMessage,
        String branchName,
        String workspacePath,
        String prUrl,
        Integer prNumber,
        String prTitle,
        Instant prReportedAt,
        Instant queuedAt,
        Instant claimedAt,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}
