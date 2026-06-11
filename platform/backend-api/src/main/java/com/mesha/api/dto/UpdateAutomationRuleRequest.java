package com.mesha.api.dto;

import com.mesha.api.model.AutomationTriggerType;
import jakarta.validation.Valid;

import java.util.List;

public record UpdateAutomationRuleRequest(
    AutomationTriggerType triggerType,
    @Valid List<AutomationActionRequest> actions,
    Boolean enabled
) {}
