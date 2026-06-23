package com.mesha.api.dto;

import com.mesha.api.model.AgentDefinition;
import com.mesha.api.model.ConnectorAgent;
import com.mesha.api.model.ConnectorAgentStatus;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public record AssignableAgentDto(
    UUID id,
    String title,
    String name,
    String providerType,
    boolean active,
    Map<String, Object> providerParameters
) {
    public static AssignableAgentDto fromDefinition(AgentDefinition a) {
        return new AssignableAgentDto(
            a.getId(),
            a.getTitle(),
            a.getName(),
            a.getProviderType().name(),
            a.isActive(),
            a.getProviderParameters()
        );
    }

    public static AssignableAgentDto fromConnector(ConnectorAgent a, Duration offlineTimeout) {
        ConnectorAgentStatus status = computeStatus(a, offlineTimeout);
        return new AssignableAgentDto(
            a.getId(),
            a.getHostname(),
            a.getExecutorType(),
            "CONNECTOR",
            status == ConnectorAgentStatus.ONLINE,
            Map.of()
        );
    }

    private static ConnectorAgentStatus computeStatus(ConnectorAgent a, Duration offlineTimeout) {
        if (a.getLastSeenAt() == null) return ConnectorAgentStatus.OFFLINE;
        return a.getLastSeenAt().plus(offlineTimeout).isAfter(java.time.Instant.now())
            ? ConnectorAgentStatus.ONLINE
            : ConnectorAgentStatus.OFFLINE;
    }
}
