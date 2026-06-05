package com.mesha.api.repository;

import com.mesha.api.model.GitHubInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubInstallationRepository extends JpaRepository<GitHubInstallation, UUID> {
    Optional<GitHubInstallation> findByInstallationId(Long installationId);
    List<GitHubInstallation> findAllByWorkspaceId(UUID workspaceId);
    List<GitHubInstallation> findAllByWorkspaceIdAndStatusNot(UUID workspaceId, String status);
    boolean existsByInstallationId(Long installationId);
}
