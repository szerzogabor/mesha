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

    @Transactional
    void deleteByFcmToken(String fcmToken);

    /**
     * All device tokens belonging to members of the given workspace. Used to fan out a
     * ticket status-change notification to everyone who can see the ticket.
     */
    @Query("""
        SELECT d FROM UserDevice d
        WHERE d.user.id IN (
            SELECT m.user.id FROM WorkspaceMember m WHERE m.workspace.id = :workspaceId
        )
        """)
    List<UserDevice> findAllForWorkspace(@Param("workspaceId") UUID workspaceId);
}
