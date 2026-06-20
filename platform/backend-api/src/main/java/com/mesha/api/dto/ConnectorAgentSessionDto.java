package com.mesha.api.dto;

import com.mesha.api.model.ConnectorAgentSession;
import com.mesha.api.model.ConnectorAgentSessionStatus;
import com.mesha.api.model.Issue;

import java.time.Instant;
import java.util.UUID;

public record ConnectorAgentSessionDto(
    UUID id,
    UUID agentId,
    UUID issueId,
    String issueIdentifier,
    String issueTitle,
    ConnectorAgentSessionStatus status,
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
) {
    public static ConnectorAgentSessionDto from(ConnectorAgentSession s) {
        return from(s, null);
    }

    public static ConnectorAgentSessionDto from(ConnectorAgentSession s, Issue issue) {
        String issueIdentifier = null;
        String issueTitle = null;
        if (issue != null) {
            String projectKey = issue.getProject() != null ? issue.getProject().getKey() : null;
            Integer issueNumber = issue.getNumber();
            issueIdentifier = (projectKey != null && issueNumber != null) ? projectKey + "-" + issueNumber : null;
            issueTitle = issue.getTitle();
        }
        return new ConnectorAgentSessionDto(
            s.getId(),
            s.getAgentId(),
            s.getIssueId(),
            issueIdentifier,
            issueTitle,
            s.getStatus(),
            s.getInstructions(),
            s.getErrorMessage(),
            s.getBranchName(),
            s.getWorkspacePath(),
            s.getPrUrl(),
            s.getPrNumber(),
            s.getPrTitle(),
            s.getPrReportedAt(),
            s.getQueuedAt(),
            s.getClaimedAt(),
            s.getStartedAt(),
            s.getCompletedAt(),
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }
}
