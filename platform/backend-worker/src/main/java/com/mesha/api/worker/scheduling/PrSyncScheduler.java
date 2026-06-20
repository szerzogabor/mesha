package com.mesha.api.worker.scheduling;

import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.service.GitHubPullRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")
public class PrSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PrSyncScheduler.class);

    private final GitHubRepositoryRepository repositoryRepo;
    private final GitHubPullRequestService prService;

    public PrSyncScheduler(GitHubRepositoryRepository repositoryRepo,
                           GitHubPullRequestService prService) {
        this.repositoryRepo = repositoryRepo;
        this.prService = prService;
    }

    @Scheduled(fixedDelayString = "${mesha.pr-sync.interval-ms:300000}", initialDelayString = "30000")
    public void syncAllRepositories() {
        List<UUID> repositoryIds;
        try {
            repositoryIds = repositoryRepo.findAllByConnectedTrue()
                    .stream()
                    .map(GitHubRepository::getId)
                    .toList();
        } catch (Exception e) {
            log.error("pr_sync_query_error error={}", e.getMessage(), e);
            return;
        }

        if (repositoryIds.isEmpty()) {
            log.debug("pr_sync_no_repositories");
            return;
        }

        log.info("pr_sync_start count={}", repositoryIds.size());

        int synced = 0;
        int errors = 0;
        for (UUID repositoryId : repositoryIds) {
            try {
                prService.syncPullRequests(repositoryId);
                synced++;
            } catch (Exception e) {
                errors++;
                log.error("pr_sync_error repositoryId={} error={}", repositoryId, e.getMessage(), e);
            }
        }

        log.info("pr_sync_end synced={} errors={}", synced, errors);
    }
}
