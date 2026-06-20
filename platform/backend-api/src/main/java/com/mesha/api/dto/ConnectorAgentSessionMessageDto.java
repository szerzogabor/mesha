package com.mesha.api.dto;

import com.mesha.api.model.ConnectorAgentSessionMessage;

import java.time.Instant;
import java.util.UUID;

public record ConnectorAgentSessionMessageDto(
    UUID id,
    UUID sessionId,
    String role,
    String content,
    Instant createdAt
) {
    public static ConnectorAgentSessionMessageDto from(ConnectorAgentSessionMessage m) {
        return new ConnectorAgentSessionMessageDto(
            m.getId(),
            m.getSession().getId(),
            m.getRole(),
            m.getContent(),
            m.getCreatedAt()
        );
    }
}
