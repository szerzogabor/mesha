package com.mesha.api.dto;

import com.mesha.api.model.TicketRuleRestrictionType;
import jakarta.validation.constraints.NotNull;

public record TicketRuleRestrictionRequest(
    @NotNull TicketRuleRestrictionType restrictionType,
    String restrictionValue
) {}
