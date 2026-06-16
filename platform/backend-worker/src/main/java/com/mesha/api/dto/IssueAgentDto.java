package com.mesha.api.dto;

import com.mesha.api.model.AgentProviderType;
import com.mesha.api.model.IssueAgent;

import java.time.Instant;
import java.util.UUID;

public record IssueAgentDto(
    UUID id,
    UUID issueId,
    UUID agentDefinitionId,
    String agentTitle,
    String agentName,
    AgentProviderType providerType,
    boolean agentActive,
    Instant assignedAt,
    UUID assignedBy
) {
    public static IssueAgentDto from(IssueAgent ia) {
        return new IssueAgentDto(
            ia.getId(),
            ia.getIssue().getId(),
            ia.getAgentDefinition().getId(),
            ia.getAgentDefinition().getTitle(),
            ia.getAgentDefinition().getName(),
            ia.getAgentDefinition().getProviderType(),
            ia.getAgentDefinition().isActive(),
            ia.getAssignedAt(),
            ia.getAssignedBy() != null ? ia.getAssignedBy().getId() : null
        );
    }
}
