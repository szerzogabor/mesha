package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateCommentRequest(
    @NotBlank String body,
    UUID parentId
) {}
