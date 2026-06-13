package com.mesha.api.dto;

import com.mesha.api.model.TicketRuleConditionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AutomationActionConditionRequest(
    @NotNull TicketRuleConditionType conditionType,
    @NotBlank @Size(max = 255) String conditionValue
) {}
