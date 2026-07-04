package com.mesha.api.repository;

import com.mesha.api.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByFcmToken(String fcmToken);

    /** System-level prune of a dead token (used when FCM reports it unregistered). */
    @Transactional
    void deleteByFcmToken(String fcmToken);

    /** User-scoped removal for the unregister endpoint — only deletes the caller's own token. */
    @Transactional
    void deleteByFcmTokenAndUserId(String fcmToken, UUID userId);

    /**
     * Device tokens belonging to members of the given workspace, excluding the actor's own
     * devices when {@code actorId} is non-null. Used to fan out a ticket status-change
     * notification. Filtering the actor in the query (rather than in memory) avoids touching
     * the lazy {@code user} association from the async, session-less send.
     */
    @Query("""
        SELECT d FROM UserDevice d
        WHERE d.user.id IN (
            SELECT m.user.id FROM WorkspaceMember m WHERE m.workspace.id = :workspaceId
        ) AND (:actorId IS NULL OR d.user.id <> :actorId)
        """)
    List<UserDevice> findAllForWorkspace(@Param("workspaceId") UUID workspaceId,
                                         @Param("actorId") UUID actorId);
}
