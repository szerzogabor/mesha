package com.mesha.api.dto;

import com.mesha.api.model.TicketRuleConditionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TicketRuleConditionRequest(
    @NotNull TicketRuleConditionType conditionType,
    @NotBlank String conditionValue
) {}
