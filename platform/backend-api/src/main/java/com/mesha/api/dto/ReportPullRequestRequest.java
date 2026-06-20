package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportPullRequestRequest(
    @NotBlank String githubUrl,
    String title,
    Integer number
) {}
