package com.mesha.api.dto;

import com.mesha.api.model.Comment;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Issue;
import com.mesha.api.model.IssueLink;
import com.mesha.api.model.IssueLinkType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything a connector needs to prepare a workspace and build a task brief for a claimed
 * session: the issue's own content plus its comments, linked issues, and target repository.
 */
public record ConnectorAgentSessionContextDto(
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
    public record CommentSummary(String author, String body, Instant createdAt) {
        public static CommentSummary from(Comment c) {
            String author = c.getAuthor() == null ? "Unknown"
                : (c.getAuthor().getName() != null && !c.getAuthor().getName().isBlank()
                    ? c.getAuthor().getName() : c.getAuthor().getEmail());
            return new CommentSummary(author, c.getBody(), c.getCreatedAt());
        }
    }

    public record RelatedIssueSummary(String identifier, String title, String status, IssueLinkType linkType) {
        public static RelatedIssueSummary from(IssueLink link, UUID issueId) {
            Issue other = link.getSourceIssue().getId().equals(issueId) ? link.getTargetIssue() : link.getSourceIssue();
            String key = other.getProject() != null ? other.getProject().getKey() : null;
            Integer number = other.getNumber();
            String identifier = (key != null && number != null) ? key + "-" + number : null;
            return new RelatedIssueSummary(identifier, other.getTitle(), other.getStatus(), link.getLinkType());
        }
    }

    public record RepositorySummary(String fullName, String htmlUrl, String cloneUrl, String defaultBranch) {
        public static RepositorySummary from(GitHubRepository repo) {
            return new RepositorySummary(repo.getFullName(), repo.getHtmlUrl(), repo.getHtmlUrl() + ".git", repo.getDefaultBranch());
        }
    }
}
