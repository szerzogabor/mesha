package com.mesha.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
    @Size(min = 1, max = 200) String name,
    String description
) {}
