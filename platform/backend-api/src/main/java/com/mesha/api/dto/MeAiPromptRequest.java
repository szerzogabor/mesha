package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Prompt payload for the per-user AI generation endpoints (/api/me/ai/*). */
public record MeAiPromptRequest(
    @NotBlank @Size(min = 1, max = 20000) String prompt
) {}
