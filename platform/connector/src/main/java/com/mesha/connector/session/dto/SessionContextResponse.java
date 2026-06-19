package com.mesha.connector.session.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirrors backend-api's {@code ConnectorAgentSessionContextDto}. */
public record SessionContextResponse(
        UUID sessionId,
        UUID issueId,
        String issueIdentifier,
        String issueTitle,
        String issueDescription,
        String issueStatus,
        String issuePriority,
        String instructions,
        List<CommentSummary> comments,
        List<RelatedIssueSummary> relatedIssues,
        RepositorySummary repository
) {
    public record CommentSummary(String author, String body, Instant createdAt) {}

    public record RelatedIssueSummary(String identifier, String title, String status, String linkType) {}

    public record RepositorySummary(String fullName, String htmlUrl, String cloneUrl, String defaultBranch) {}
}
