package com.mesha.api.repository;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.BlocksSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlocksSessionRepository extends JpaRepository<BlocksSession, UUID> {

    @Query("SELECT s FROM BlocksSession s WHERE s.issue.id = :issueId ORDER BY s.createdAt DESC")
    List<BlocksSession> findByIssueIdOrderByCreatedAtDesc(UUID issueId);

    @Query("SELECT s FROM BlocksSession s WHERE s.issue.id = :issueId AND s.executionState NOT IN ('DONE', 'FAILED', 'CANCELED') ORDER BY s.createdAt DESC")
    Optional<BlocksSession> findActiveByIssueId(UUID issueId);

    List<BlocksSession> findByExecutionState(AIExecutionState executionState);

    Optional<BlocksSession> findByProviderSessionId(String providerSessionId);
}
