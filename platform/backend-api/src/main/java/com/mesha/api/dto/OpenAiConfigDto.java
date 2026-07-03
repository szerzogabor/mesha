package com.mesha.api.dto;

import com.mesha.api.model.OpenAiAuthMode;
import com.mesha.api.model.UserOpenAiConfig;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe view of a user's OpenAI config — never exposes secret material
 * (API key or tokens), only metadata the UI needs.
 */
public record OpenAiConfigDto(
    UUID id,
    UUID userId,
    OpenAiAuthMode authMode,
    String accountId,
    String model,
    String status,
    Instant connectedAt,
    Instant updatedAt
) {
    public static OpenAiConfigDto from(UserOpenAiConfig c) {
        return new OpenAiConfigDto(
            c.getId(),
            c.getUser().getId(),
            c.getAuthMode(),
            c.getAccountId(),
            c.getModel(),
            c.getStatus(),
            c.getConnectedAt(),
            c.getUpdatedAt()
        );
    }
}
