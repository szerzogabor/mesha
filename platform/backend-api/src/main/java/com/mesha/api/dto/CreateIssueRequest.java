package com.mesha.api.dto;

import com.mesha.api.model.IssuePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateIssueRequest(
    @NotBlank @Size(min = 1, max = 500) String title,
    String description,
    String status,
    IssuePriority priority,
    UUID assigneeId,
    List<UUID> labelIds
) {}
