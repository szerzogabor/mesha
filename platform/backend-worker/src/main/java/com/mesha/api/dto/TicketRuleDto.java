package com.mesha.api.dto;

import com.mesha.api.model.TicketRule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketRuleDto(
    UUID id,
    UUID projectId,
    String name,
    boolean enabled,
    List<TicketRuleConditionDto> conditions,
    List<TicketRuleRestrictionDto> restrictions,
    Instant createdAt,
    Instant updatedAt
) {
    public static TicketRuleDto from(TicketRule r) {
        return new TicketRuleDto(
            r.getId(),
            r.getProject().getId(),
            r.getName(),
            r.isEnabled(),
            r.getConditions().stream().map(TicketRuleConditionDto::from).toList(),
            r.getRestrictions().stream().map(TicketRuleRestrictionDto::from).toList(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
