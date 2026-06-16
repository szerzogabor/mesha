package com.mesha.api.repository;

import com.mesha.api.model.WorkspaceMember;
import com.mesha.api.model.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    List<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId);
    boolean existsByWorkspaceIdAndUserIdAndRoleIn(UUID workspaceId, UUID userId, List<WorkspaceRole> roles);
}
