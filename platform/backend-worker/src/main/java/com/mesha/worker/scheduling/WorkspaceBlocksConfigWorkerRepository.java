package com.mesha.worker.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface WorkspaceBlocksConfigWorkerRepository
        extends JpaRepository<WorkspaceBlocksConfigWorkerRecord, UUID> {

    @Query(value = """
            SELECT wbc.api_key_enc
            FROM workspace_blocks_config wbc
            JOIN projects p ON p.workspace_id = wbc.workspace_id
            JOIN issues i ON i.project_id = p.id
            WHERE i.id = :issueId
            """, nativeQuery = true)
    Optional<String> findApiKeyEncByIssueId(@Param("issueId") UUID issueId);
}
