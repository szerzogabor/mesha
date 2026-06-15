package com.mesha.api.repository;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.BlocksSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BlocksSessionRepository extends JpaRepository<BlocksSession, UUID> {

    @Query("SELECT s FROM BlocksSession s WHERE s.issue.id = :issueId ORDER BY s.createdAt DESC")
    List<BlocksSession> findByIssueIdOrderByCreatedAtDesc(UUID issueId);

    @Query("SELECT s FROM BlocksSession s WHERE s.issue.id = :issueId AND s.executionState NOT IN ('DONE', 'FAILED', 'CANCELED') ORDER BY s.createdAt DESC")
    Optional<BlocksSession> findActiveByIssueId(UUID issueId);

    List<BlocksSession> findByExecutionState(AIExecutionState executionState);

    Optional<BlocksSession> findFirstByProviderSessionIdOrderByCreatedAtDesc(String providerSessionId);

    Optional<BlocksSession> findFirstByBranchName(String branchName);

    @Query("SELECT s FROM BlocksSession s WHERE s.executionState NOT IN :states")
    List<BlocksSession> findAllByExecutionStateNotIn(@Param("states") Collection<AIExecutionState> states);

    @Query("""
           SELECT s FROM BlocksSession s
           WHERE s.executionState NOT IN :terminalStates
             AND s.issue.project.workspace.id = :workspaceId
             AND UPPER(s.issue.project.key) = :projectKey
             AND s.issue.number = :issueNumber
           ORDER BY s.createdAt DESC
           """)
    List<BlocksSession> findActiveSessionsByProjectKeyAndIssueNumber(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectKey") String projectKey,
            @Param("issueNumber") Integer issueNumber,
            @Param("terminalStates") Set<AIExecutionState> terminalStates);

    @Query("""
           SELECT s FROM BlocksSession s
           WHERE s.issue.project.workspace.id = :workspaceId
             AND UPPER(s.issue.project.key) = :projectKey
             AND s.issue.number = :issueNumber
           ORDER BY s.createdAt DESC
           """)
    List<BlocksSession> findSessionsByProjectKeyAndIssueNumber(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectKey") String projectKey,
            @Param("issueNumber") Integer issueNumber);

    /**
     * Atomically claims a session for dispatch by transitioning execution_state from CREATED to
     * DISPATCHING. Returns 1 if the claim succeeded (this worker owns the dispatch), 0 if another
     * worker already claimed or dispatched the session.
     * Pass {@code Instant.now()} from the application to keep updated_at on the application clock,
     * consistent with @PreUpdate and the isDispatchingStale comparison.
     */
    @Modifying
    @Query(value = "UPDATE blocks_sessions SET execution_state = 'DISPATCHING', updated_at = :now WHERE id = :sessionId AND execution_state = 'CREATED'", nativeQuery = true)
    int claimForDispatch(@Param("sessionId") UUID sessionId, @Param("now") java.time.Instant now);

    /**
     * Reverts a stale DISPATCHING claim back to CREATED so the next poll cycle can retry.
     * Used for pod-crash recovery when a session is stuck in DISPATCHING with no provider_session_id.
     * Pass {@code Instant.now()} from the application to keep updated_at on the application clock,
     * consistent with @PreUpdate and the isDispatchingStale comparison.
     */
    @Modifying
    @Query(value = "UPDATE blocks_sessions SET execution_state = 'CREATED', updated_at = :now WHERE id = :sessionId AND execution_state = 'DISPATCHING'", nativeQuery = true)
    int revertDispatchClaim(@Param("sessionId") UUID sessionId, @Param("now") java.time.Instant now);
}
