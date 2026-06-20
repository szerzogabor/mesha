package com.mesha.api.dto;

import com.mesha.api.model.TicketRuleConditionType;
import jakarta.validation.constraints.NotNull;

public record AutomationActionConditionRequest(
    @NotNull TicketRuleConditionType conditionType,
    String conditionValue
) {}
