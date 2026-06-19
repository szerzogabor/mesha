package com.mesha.api.repository;

import com.mesha.api.model.ConnectorAgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorAgentSessionRepository extends JpaRepository<ConnectorAgentSession, UUID> {

    List<ConnectorAgentSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ConnectorAgentSession> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Locks the oldest queued session owned by {@code userId} so it can be claimed without
     * racing other connector instances polling for work. Must run inside the same transaction
     * as {@link #claim}.
     */
    @Query(value = """
           SELECT id FROM connector_agent_sessions
           WHERE status = 'QUEUED' AND user_id = :userId
           ORDER BY queued_at ASC
           LIMIT 1
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    Optional<UUID> findNextQueuedIdForUpdate(@Param("userId") UUID userId);

    /**
     * Atomically transitions a QUEUED session to CLAIMED. Returns 1 on success, 0 if another
     * connector already claimed it between the lookup and this update.
     */
    @Modifying
    @Query(value = """
           UPDATE connector_agent_sessions
           SET status = 'CLAIMED', agent_id = :agentId, claimed_at = :now, updated_at = :now
           WHERE id = :id AND status = 'QUEUED'
           """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("agentId") UUID agentId, @Param("now") Instant now);
}
