package com.mesha.api.dto;

import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAutomationRuleRequest(
    @NotNull AutomationTriggerType triggerType,
    @NotNull AutomationActionType actionType,
    @NotBlank @Size(max = 255) String actionValue
) {}
