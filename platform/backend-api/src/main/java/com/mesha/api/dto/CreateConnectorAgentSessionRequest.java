package com.mesha.api.dto;

import java.util.UUID;

public record CreateConnectorAgentSessionRequest(
    UUID issueId,
    String instructions
) {}
