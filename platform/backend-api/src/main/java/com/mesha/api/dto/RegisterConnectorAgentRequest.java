package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegisterConnectorAgentRequest(
    @NotBlank @Size(max = 255) String hostname,
    @NotBlank @Size(max = 100) String executorType,
    @NotBlank @Size(max = 50) String connectorVersion,
    @NotNull List<String> capabilities
) {}
