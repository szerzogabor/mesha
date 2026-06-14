package com.mesha.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignAgentRequest(
    @NotNull UUID agentDefinitionId
) {}
