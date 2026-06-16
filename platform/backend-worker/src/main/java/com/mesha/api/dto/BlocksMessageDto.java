package com.mesha.api.dto;

import com.mesha.api.model.BlocksMessage;

import java.time.Instant;
import java.util.UUID;

public record BlocksMessageDto(
    UUID id,
    UUID sessionId,
    String message,
    String role,
    Instant createdAt
) {
    public static BlocksMessageDto from(BlocksMessage m) {
        return new BlocksMessageDto(
            m.getId(),
            m.getSession().getId(),
            m.getMessage(),
            m.getRole(),
            m.getCreatedAt()
        );
    }
}
