package com.mesha.connector.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        String hostname,
        String executorType,
        String connectorVersion,
        List<String> capabilities,
        String status,
        Instant registeredAt,
        Instant lastSeenAt
) {}
