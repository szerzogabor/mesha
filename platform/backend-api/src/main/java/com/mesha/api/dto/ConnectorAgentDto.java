package com.mesha.api.dto;

import com.mesha.api.model.ConnectorAgent;
import com.mesha.api.model.ConnectorAgentStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConnectorAgentDto(
    UUID id,
    String hostname,
    String executorType,
    String connectorVersion,
    List<String> capabilities,
    ConnectorAgentStatus status,
    Instant registeredAt,
    Instant lastSeenAt
) {
    public static ConnectorAgentDto from(ConnectorAgent agent, Duration offlineTimeout) {
        return new ConnectorAgentDto(
            agent.getId(),
            agent.getHostname(),
            agent.getExecutorType(),
            agent.getConnectorVersion(),
            agent.getCapabilities(),
            computeStatus(agent.getLastSeenAt(), offlineTimeout),
            agent.getRegisteredAt(),
            agent.getLastSeenAt()
        );
    }

    private static ConnectorAgentStatus computeStatus(Instant lastSeenAt, Duration offlineTimeout) {
        if (lastSeenAt == null) {
            return ConnectorAgentStatus.OFFLINE;
        }
        boolean withinTimeout = lastSeenAt.plus(offlineTimeout).isAfter(Instant.now());
        return withinTimeout ? ConnectorAgentStatus.ONLINE : ConnectorAgentStatus.OFFLINE;
    }
}
