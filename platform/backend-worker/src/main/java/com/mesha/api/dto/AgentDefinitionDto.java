package com.mesha.api.dto;

import com.mesha.api.model.AgentDefinition;
import com.mesha.api.model.AgentProviderType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentDefinitionDto(
    UUID id,
    UUID workspaceId,
    String name,
    String title,
    String description,
    AgentProviderType providerType,
    String systemPrompt,
    Map<String, Object> providerParameters,
    String blocksAgentName,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static AgentDefinitionDto from(AgentDefinition a) {
        return new AgentDefinitionDto(
            a.getId(),
            a.getWorkspace().getId(),
            a.getName(),
            a.getTitle(),
            a.getDescription(),
            a.getProviderType(),
            a.getSystemPrompt(),
            a.getProviderParameters(),
            a.getBlocksAgentName(),
            a.isActive(),
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }
}
