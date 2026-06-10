package com.mesha.api.dto;

import com.mesha.api.model.IssuePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveDraftRequest(
    @NotBlank @Size(min = 1, max = 500) String title,
    String description,
    String status,
    IssuePriority priority
) {}
