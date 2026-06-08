package com.mesha.worker.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface GitHubRepositoryWorkerRepository extends JpaRepository<GitHubRepositoryWorkerRecord, UUID> {

    @Query(value = """
            SELECT r.* FROM github_repositories r
            JOIN projects p ON p.workspace_id = r.workspace_id
            JOIN issues i ON i.project_id = p.id
            WHERE i.id = :issueId AND r.connected = true
            LIMIT 1
            """, nativeQuery = true)
    List<GitHubRepositoryWorkerRecord> findConnectedByIssueId(@Param("issueId") UUID issueId);
}
