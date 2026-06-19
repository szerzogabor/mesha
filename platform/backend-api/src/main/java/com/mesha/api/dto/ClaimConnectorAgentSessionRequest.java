package com.mesha.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClaimConnectorAgentSessionRequest(@NotNull UUID agentId) {}
