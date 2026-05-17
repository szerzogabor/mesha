package com.mesha.api.dto;

import com.mesha.api.model.Issue;
import com.mesha.api.model.IssueStatus;
import com.mesha.api.model.IssuePriority;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueDto(
    UUID id,
    UUID projectId,
    String title,
    String description,
    IssueStatus status,
    IssuePriority priority,
    UserDto assignee,
    List<LabelDto> labels,
    String aiAssignmentState,
    Instant createdAt,
    Instant updatedAt
) {
    public static IssueDto from(Issue i) {
        return new IssueDto(
            i.getId(),
            i.getProject().getId(),
            i.getTitle(),
            i.getDescription(),
            i.getStatus(),
            i.getPriority(),
            i.getAssignee() != null ? UserDto.from(i.getAssignee()) : null,
            i.getLabels().stream().map(LabelDto::from).toList(),
            i.getAiAssignmentState(),
            i.getCreatedAt(),
            i.getUpdatedAt()
        );
    }
}
