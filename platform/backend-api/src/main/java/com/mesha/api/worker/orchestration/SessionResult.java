package com.mesha.api.worker.orchestration;

import java.util.List;

public record SessionResult(
        String providerSessionId,
        SessionStatus status,
        String finalMessage,
        String workspaceId,
        String sessionHtmlUrl,
        List<String> messages
) {
    public enum SessionStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
