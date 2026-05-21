package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blocks_sessions",
       indexes = {
           @Index(name = "idx_blocks_sessions_issue_id", columnList = "issue_id"),
           @Index(name = "idx_blocks_sessions_execution_state", columnList = "execution_state")
       })
public class BlocksSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @Column(nullable = false, length = 50)
    private String provider = "blocks";

    @Column(name = "provider_session_id")
    private String providerSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_state", nullable = false, length = 30)
    private AIExecutionState executionState = AIExecutionState.CREATED;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "pr_url")
    private String prUrl;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Issue getIssue() { return issue; }
    public void setIssue(Issue issue) { this.issue = issue; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderSessionId() { return providerSessionId; }
    public void setProviderSessionId(String providerSessionId) { this.providerSessionId = providerSessionId; }
    public AIExecutionState getExecutionState() { return executionState; }
    public void setExecutionState(AIExecutionState executionState) { this.executionState = executionState; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
