package com.mesha.api.worker.orchestration;

public record SessionResult(
        String providerSessionId,
        SessionStatus status,
        String finalMessage,
        String workspaceId
) {
    public enum SessionStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
