package com.mesha.api.repository;

import com.mesha.api.model.GitHubAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GitHubAuditLogRepository extends JpaRepository<GitHubAuditLog, UUID> {
    List<GitHubAuditLog> findAllByInstallationIdOrderByCreatedAtDesc(UUID installationId);
}
