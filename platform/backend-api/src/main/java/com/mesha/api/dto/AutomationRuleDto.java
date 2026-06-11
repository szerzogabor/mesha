package com.mesha.api.dto;

import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationTriggerType;

import java.time.Instant;
import java.util.UUID;

public record AutomationRuleDto(
    UUID id,
    UUID projectId,
    AutomationTriggerType triggerType,
    AutomationActionType actionType,
    String actionValue,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
    public static AutomationRuleDto from(AutomationRule r) {
        return new AutomationRuleDto(
            r.getId(),
            r.getProject().getId(),
            r.getTriggerType(),
            r.getActionType(),
            r.getActionValue(),
            r.isEnabled(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
