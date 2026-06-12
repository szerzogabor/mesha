package com.mesha.api.dto;

import jakarta.validation.Valid;

import java.util.List;

public record UpdateTicketRuleRequest(
    String name,
    Boolean enabled,
    @Valid List<TicketRuleConditionRequest> conditions,
    @Valid List<TicketRuleRestrictionRequest> restrictions
) {}
