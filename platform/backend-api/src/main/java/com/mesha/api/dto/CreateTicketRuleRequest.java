package com.mesha.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateTicketRuleRequest(
    @NotBlank String name,
    @NotNull @NotEmpty @Valid List<TicketRuleConditionRequest> conditions,
    @NotNull @NotEmpty @Valid List<TicketRuleRestrictionRequest> restrictions
) {}
