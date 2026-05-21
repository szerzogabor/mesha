package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateDraftRequest(
    @NotBlank @Size(min = 10, max = 2000) String prompt
) {}
