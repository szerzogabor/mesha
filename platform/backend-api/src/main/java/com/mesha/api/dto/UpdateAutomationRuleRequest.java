package com.mesha.api.dto;

import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationTriggerType;
import jakarta.validation.constraints.Size;

public record UpdateAutomationRuleRequest(
    AutomationTriggerType triggerType,
    AutomationActionType actionType,
    @Size(max = 255) String actionValue,
    Boolean enabled
) {}
