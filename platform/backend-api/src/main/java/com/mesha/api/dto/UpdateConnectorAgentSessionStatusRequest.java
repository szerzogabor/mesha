package com.mesha.api.dto;

import com.mesha.api.model.ConnectorAgentSessionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateConnectorAgentSessionStatusRequest(
    @NotNull ConnectorAgentSessionStatus status,
    String errorMessage
) {}
