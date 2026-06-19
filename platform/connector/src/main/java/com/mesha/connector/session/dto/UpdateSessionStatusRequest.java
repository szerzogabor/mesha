package com.mesha.connector.session.dto;

import com.mesha.connector.session.ConnectorSessionStatus;

public record UpdateSessionStatusRequest(
        ConnectorSessionStatus status,
        String errorMessage,
        String branchName,
        String workspacePath
) {}
