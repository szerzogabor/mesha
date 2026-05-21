package com.mesha.api.dto;

import com.mesha.api.model.AIExecutionState;

public record UpdateBlocksSessionRequest(
    AIExecutionState executionState,
    String providerSessionId,
    String prUrl,
    Integer prNumber,
    String branchName,
    String errorMessage
) {}
