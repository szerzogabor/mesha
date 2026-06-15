package com.mesha.api.dto;

import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.Issue;
import com.mesha.api.model.IssuePriority;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueDto(
    UUID id,
    UUID projectId,
    String identifier,
    String title,
    String description,
    String status,
    IssuePriority priority,
    UserDto assignee,
    List<LabelDto> labels,
    String aiAssignmentState,
    String agentType,
    String agentLlm,
    int position,
    Instant createdAt,
    Instant updatedAt,
    GitHubPullRequestDto lastPullRequest
) {
    public static IssueDto from(Issue i) {
        return from(i, null);
    }

    public static IssueDto from(Issue i, GitHubPullRequest lastPr) {
        String projectKey = i.getProject() != null ? i.getProject().getKey() : null;
        Integer number = i.getNumber();
        String identifier = (projectKey != null && number != null) ? projectKey + "-" + number : null;
        return new IssueDto(
            i.getId(),
            i.getProject().getId(),
            identifier,
            i.getTitle(),
            i.getDescription(),
            i.getStatus(),
            i.getPriority(),
            i.getAssignee() != null ? UserDto.from(i.getAssignee()) : null,
            i.getLabels().stream().map(LabelDto::from).toList(),
            i.getAiAssignmentState(),
            i.getAgentType(),
            i.getAgentLlm(),
            i.getPosition(),
            i.getCreatedAt(),
            i.getUpdatedAt(),
            lastPr != null ? GitHubPullRequestDto.from(lastPr) : null
        );
    }
}
