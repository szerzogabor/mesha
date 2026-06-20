package com.mesha.connector.session.dto;

import java.time.Instant;
import java.util.UUID;

/** Mirrors backend-api's {@code ConnectorAgentSessionMessageDto}. */
public record SessionMessageResponse(
        UUID id,
        UUID sessionId,
        String role,
        String content,
        Instant createdAt
) {}
