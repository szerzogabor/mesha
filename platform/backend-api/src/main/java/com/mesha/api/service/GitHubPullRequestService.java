package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.dto.GitHubPullRequestDto;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GitHubPullRequestService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestService.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final GitHubPullRequestRepository prRepo;
    private final GitHubRepositoryRepository repositoryRepo;
    private final GitHubInstallationRepository installationRepo;
    private final GitHubAppService appService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubPullRequestService(GitHubPullRequestRepository prRepo,
                                    GitHubRepositoryRepository repositoryRepo,
                                    GitHubInstallationRepository installationRepo,
                                    GitHubAppService appService,
                                    ObjectMapper objectMapper) {
        this.prRepo = prRepo;
        this.repositoryRepo = repositoryRepo;
        this.installationRepo = installationRepo;
        this.appService = appService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<GitHubPullRequestDto> listForRepository(UUID repositoryId) {
        return prRepo.findAllByRepositoryId(repositoryId)
                .stream().map(GitHubPullRequestDto::from).toList();
    }

    public GitHubPullRequestDto getById(UUID prId) {
        return prRepo.findById(prId)
                .map(GitHubPullRequestDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pull request not found"));
    }

    /**
     * Syncs all open pull requests from GitHub for the given repository.
     */
    @Transactional
    public List<GitHubPullRequestDto> syncPullRequests(UUID repositoryId) {
        MDC.put("repositoryId", repositoryId.toString());
        long startMs = System.currentTimeMillis();
        try {
            GitHubRepository repo = repositoryRepo.findById(repositoryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));

            MDC.put("repoFullName", repo.getFullName());
            log.info("pr_sync_start repo={} repository_id={}", repo.getFullName(), repositoryId);

            GitHubInstallation installation = repo.getInstallation();
            String token = appService.getInstallationToken(installation.getInstallationId());

            String endpoint = "/repos/" + repo.getFullName() + "/pulls?state=all&per_page=100&sort=updated&direction=desc";
            long apiStartMs = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + endpoint))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long apiDurationMs = System.currentTimeMillis() - apiStartMs;
            log.debug("github_api_call endpoint={} status={} duration_ms={}", endpoint, response.statusCode(), apiDurationMs);

            JsonNode prs = objectMapper.readTree(response.body());
            int[] upsertCount = {0};
            prs.forEach(pr -> {
                upsertPullRequest(repo, pr);
                upsertCount[0]++;
            });
            repo.setLastSyncedAt(Instant.now());
            repositoryRepo.save(repo);

            long totalDurationMs = System.currentTimeMillis() - startMs;
            log.info("pr_sync_complete repo={} pr_count={} duration_ms={}", repo.getFullName(), upsertCount[0], totalDurationMs);

            return prRepo.findAllByRepositoryId(repositoryId)
                    .stream().map(GitHubPullRequestDto::from).toList();
        } catch (ResponseStatusException e) {
            log.warn("pr_sync_failed repository_id={} reason={}", repositoryId, e.getReason());
            throw e;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("pr_sync_error repository_id={} duration_ms={}", repositoryId, durationMs, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to sync pull requests: " + e.getMessage());
        } finally {
            MDC.remove("repositoryId");
            MDC.remove("repoFullName");
        }
    }

    /**
     * Handles an incoming pull_request webhook event.
     */
    @Transactional
    public void handlePullRequestEvent(JsonNode payload) {
        String action = payload.path("action").asText("unknown");
        JsonNode prNode = payload.path("pull_request");
        JsonNode repoNode = payload.path("repository");
        String fullName = repoNode.path("full_name").asText("unknown");
        int prNumber = prNode.path("number").asInt(0);
        String state = prNode.path("state").asText("unknown");

        MDC.put("repoFullName", fullName);
        MDC.put("prNumber", String.valueOf(prNumber));
        try {
            log.info("pr_event_received action={} pr_number={} state={} repo={}", action, prNumber, state, fullName);
            repositoryRepo.findByFullName(fullName).ifPresentOrElse(
                    repo -> {
                        upsertPullRequest(repo, prNode);
                        log.info("pr_event_processed action={} pr_number={} repo={}", action, prNumber, fullName);
                    },
                    () -> log.debug("pr_event_repo_not_found repo={} pr_number={}", fullName, prNumber)
            );
        } finally {
            MDC.remove("repoFullName");
            MDC.remove("prNumber");
        }
    }

    private void upsertPullRequest(GitHubRepository repo, JsonNode prNode) {
        int prNumber = prNode.path("number").asInt();
        String state = prNode.path("state").asText("open");
        Optional<GitHubPullRequest> existing =
                prRepo.findByRepositoryIdAndGithubPrNumber(repo.getId(), prNumber);
        boolean isNew = existing.isEmpty();

        GitHubPullRequest pr = existing.orElse(new GitHubPullRequest());
        pr.setRepository(repo);
        pr.setGithubPrNumber(prNumber);
        pr.setTitle(prNode.path("title").asText());
        pr.setBody(prNode.path("body").asText(null));
        pr.setState(state);
        pr.setAuthorLogin(prNode.path("user").path("login").asText(null));
        pr.setAuthorAvatarUrl(prNode.path("user").path("avatar_url").asText(null));
        pr.setSourceBranch(prNode.path("head").path("ref").asText());
        pr.setTargetBranch(prNode.path("base").path("ref").asText());
        pr.setHtmlUrl(prNode.path("html_url").asText());
        pr.setDraft(prNode.path("draft").asBoolean(false));
        pr.setCommitsCount(prNode.path("commits").asInt(0));

        if (prNode.hasNonNull("merged_at")) {
            pr.setMergedAt(Instant.parse(prNode.get("merged_at").asText()));
        }
        if (prNode.hasNonNull("closed_at")) {
            pr.setClosedAt(Instant.parse(prNode.get("closed_at").asText()));
        }

        prRepo.save(pr);
        log.debug("pr_upsert action={} pr_number={} state={} repo={}",
                isNew ? "created" : "updated", prNumber, state, repo.getFullName());
    }
}
