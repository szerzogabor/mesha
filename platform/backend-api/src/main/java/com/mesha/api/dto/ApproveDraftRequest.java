package com.mesha.api.dto;

import com.mesha.api.model.IssuePriority;
import com.mesha.api.model.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveDraftRequest(
    @NotBlank @Size(min = 1, max = 500) String title,
    String description,
    IssueStatus status,
    IssuePriority priority
) {}
