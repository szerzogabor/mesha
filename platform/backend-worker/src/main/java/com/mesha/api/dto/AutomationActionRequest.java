package com.mesha.api.dto;

import com.mesha.api.model.AutomationActionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AutomationActionRequest(
    @NotNull AutomationActionType actionType,
    @Size(max = 255) String actionValue,
    @Valid List<AutomationActionConditionRequest> conditions
) {}
