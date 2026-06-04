package com.mesha.api.repository;

import com.mesha.api.model.GitHubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, UUID> {
    List<GitHubRepository> findAllByWorkspaceId(UUID workspaceId);

    @Query("""
            SELECT r FROM GitHubRepository r
            JOIN FETCH r.installation i
            WHERE r.workspace.id = :workspaceId
              AND r.connected = true
              AND i.status = 'active'
            """)
    List<GitHubRepository> findConnectedWithActiveInstallationByWorkspaceId(
            @Param("workspaceId") UUID workspaceId);
    List<GitHubRepository> findAllByInstallationId(UUID installationId);
    Optional<GitHubRepository> findByFullName(String fullName);
    Optional<GitHubRepository> findByGithubRepoId(Long githubRepoId);
    boolean existsByFullName(String fullName);
}
