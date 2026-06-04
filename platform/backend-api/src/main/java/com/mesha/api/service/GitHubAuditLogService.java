package com.mesha.api.service;

import com.mesha.api.model.GitHubAuditLog;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.repository.GitHubAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(GitHubAuditLogService.class);

    public static final String INSTALLATION_CREATED     = "INSTALLATION_CREATED";
    public static final String INSTALLATION_DELETED     = "INSTALLATION_DELETED";
    public static final String INSTALLATION_SUSPENDED   = "INSTALLATION_SUSPENDED";
    public static final String INSTALLATION_UNSUSPENDED = "INSTALLATION_UNSUSPENDED";
    public static final String INSTALLATION_REFRESHED   = "INSTALLATION_REFRESHED";
    public static final String REPOSITORY_DETACHED      = "REPOSITORY_DETACHED";

    private final GitHubAuditLogRepository auditLogRepo;

    public GitHubAuditLogService(GitHubAuditLogRepository auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    public void log(GitHubInstallation installation, String eventType, String details) {
        GitHubAuditLog entry = new GitHubAuditLog();
        entry.setInstallation(installation);
        entry.setEventType(eventType);
        entry.setDetails(details);
        auditLogRepo.save(entry);
        log.info("GitHub audit event eventType={} installationId={}",
                eventType, installation != null ? installation.getId() : null);
    }
}
