package com.mesha.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
    @Size(min = 1, max = 200) String name,
    String description,
    @Size(min = 2, max = 10) @Pattern(regexp = "[A-Za-z0-9]*", message = "Key must contain only letters and digits") String key
) {}
