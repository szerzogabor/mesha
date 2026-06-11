package com.mesha.api.dto;

import com.mesha.api.model.AutomationActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AutomationActionRequest(
    @NotNull AutomationActionType actionType,
    @NotBlank @Size(max = 255) String actionValue
) {}
