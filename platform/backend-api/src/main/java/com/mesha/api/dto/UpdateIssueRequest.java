package com.mesha.api.dto;

import com.mesha.api.model.IssueStatus;
import com.mesha.api.model.IssuePriority;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateIssueRequest(
    @Size(min = 1, max = 500) String title,
    String description,
    IssueStatus status,
    IssuePriority priority,
    UUID assigneeId,
    Boolean clearAssignee,
    List<UUID> labelIds
) {}
