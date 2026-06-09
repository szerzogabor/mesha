package com.mesha.api.repository;

import com.mesha.api.model.WorkspaceBlocksConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceBlocksConfigRepository extends JpaRepository<WorkspaceBlocksConfig, UUID> {

    Optional<WorkspaceBlocksConfig> findByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);

    void deleteByWorkspaceId(UUID workspaceId);

    @Query(value = """
            SELECT wbc.api_key_enc
            FROM workspace_blocks_config wbc
            JOIN projects p ON p.workspace_id = wbc.workspace_id
            JOIN issues i ON i.project_id = p.id
            WHERE i.id = :issueId
            """, nativeQuery = true)
    Optional<String> findApiKeyEncByIssueId(@Param("issueId") UUID issueId);

    @Query(value = """
            SELECT wbc.blocks_workspace_id
            FROM workspace_blocks_config wbc
            JOIN projects p ON p.workspace_id = wbc.workspace_id
            JOIN issues i ON i.project_id = p.id
            WHERE i.id = :issueId
            """, nativeQuery = true)
    Optional<String> findBlocksWorkspaceIdByIssueId(@Param("issueId") UUID issueId);
}
