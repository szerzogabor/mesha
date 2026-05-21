package com.mesha.api.repository;

import com.mesha.api.model.GitHubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, UUID> {
    List<GitHubRepository> findAllByWorkspaceId(UUID workspaceId);
    List<GitHubRepository> findAllByInstallationId(UUID installationId);
    Optional<GitHubRepository> findByFullName(String fullName);
    Optional<GitHubRepository> findByGithubRepoId(Long githubRepoId);
    boolean existsByFullName(String fullName);
}
