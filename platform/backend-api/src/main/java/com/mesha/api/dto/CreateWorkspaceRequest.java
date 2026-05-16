package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
    @NotBlank @Size(min = 1, max = 100) String name,
    @NotBlank @Pattern(regexp = "^[a-z0-9-]{2,50}$",
        message = "Slug must be 2-50 lowercase letters, digits, or hyphens")
    String slug
) {}
