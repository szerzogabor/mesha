package com.mesha.api.dto;

import com.mesha.api.model.AutomationTriggerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateAutomationRuleRequest(
    @NotNull AutomationTriggerType triggerType,
    @NotEmpty @Valid List<AutomationActionRequest> actions
) {}
