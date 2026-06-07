package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimal JPA projection of the blocks_sessions table used exclusively by the
 * polling scheduler. The full entity with all business associations lives in
 * backend-api; this view covers only the fields needed here.
 */
@Entity
@Table(name = "blocks_sessions")
public class BlocksSessionRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "issue_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "provider_session_id")
    private String providerSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_state", nullable = false, length = 30)
    private AIExecutionState executionState;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIssueId() { return issueId; }
    public String getProviderSessionId() { return providerSessionId; }
    public void setProviderSessionId(String providerSessionId) { this.providerSessionId = providerSessionId; }
    public AIExecutionState getExecutionState() { return executionState; }
    public void setExecutionState(AIExecutionState executionState) { this.executionState = executionState; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
