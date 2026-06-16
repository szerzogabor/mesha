package com.mesha.api.dto;

import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationTriggerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AutomationRuleDto(
    UUID id,
    UUID projectId,
    AutomationTriggerType triggerType,
    String triggerValue,
    List<AutomationActionDto> actions,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
    public static AutomationRuleDto from(AutomationRule r) {
        return new AutomationRuleDto(
            r.getId(),
            r.getProject().getId(),
            r.getTriggerType(),
            r.getTriggerValue(),
            r.getActions().stream().map(AutomationActionDto::from).toList(),
            r.isEnabled(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
