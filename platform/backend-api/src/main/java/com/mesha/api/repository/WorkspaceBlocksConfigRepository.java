package com.mesha.api.repository;

import com.mesha.api.model.WorkspaceBlocksConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceBlocksConfigRepository extends JpaRepository<WorkspaceBlocksConfig, UUID> {

    Optional<WorkspaceBlocksConfig> findByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);

    void deleteByWorkspaceId(UUID workspaceId);
}
