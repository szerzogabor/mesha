package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportPullRequestRequest(
    @NotBlank String githubUrl,
    @Size(max = 500) String title,
    Integer number
) {}
