package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.dto.GitHubPullRequestDto;
import com.mesha.api.github.GitHubInstallationStatus;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
        long startMs = System.currentTimeMillis();
        List<GitHubPullRequestDto> prs = prRepo.findAllByRepositoryId(repositoryId)
                .stream().map(GitHubPullRequestDto::from).toList();
        log.info("Listed pull requests repositoryId={} count={} durationMs={}", repositoryId, prs.size(), System.currentTimeMillis() - startMs);
        return prs;
    }

    public GitHubPullRequestDto getById(UUID prId) {
        long startMs = System.currentTimeMillis();
        GitHubPullRequestDto pr = prRepo.findById(prId)
                .map(GitHubPullRequestDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pull request not found"));
        log.info("Fetched pull request prId={} durationMs={}", prId, System.currentTimeMillis() - startMs);
        return pr;
    }

    /**
     * Syncs all open pull requests from GitHub for the given repository.
     */
    public List<GitHubPullRequestDto> syncPullRequests(UUID repositoryId) {
        log.info("Syncing pull requests from GitHub repositoryId={}", repositoryId);

        GitHubRepository repo = repositoryRepo.findById(repositoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));

        if (!Boolean.TRUE.equals(repo.getConnected())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Repository is not connected");
        }

        GitHubInstallation installation = repo.getInstallation();
        if (installation == null || !GitHubInstallationStatus.isActive(installation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "GitHub App installation is not active");
        }
        log.debug("Fetching installation token for PR sync repositoryId={} installationId={} fullName={}",
                repositoryId, installation.getInstallationId(), repo.getFullName());
        String token = appService.getInstallationToken(installation.getInstallationId());

        try {
            String nextUrl = GITHUB_API + "/repos/" + repo.getFullName()
                    + "/pulls?state=all&per_page=100&sort=updated&direction=desc";
            int[] counts = {0, 0};
            int pageNum = 1;
            long totalStart = System.currentTimeMillis();

            while (nextUrl != null) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(nextUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                long start = System.currentTimeMillis();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long durationMs = System.currentTimeMillis() - start;
                log.debug("GitHub pull requests API page={} repositoryId={} fullName={} httpStatus={} durationMs={}",
                        pageNum, repositoryId, repo.getFullName(), response.statusCode(), durationMs);

                if (response.statusCode() != 200) {
                    log.error("GitHub pull requests API returned non-200 status page={} repositoryId={} fullName={} httpStatus={} body={}",
                            pageNum, repositoryId, repo.getFullName(), response.statusCode(), response.body());
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "GitHub API returned status " + response.statusCode());
                }

                JsonNode prs = objectMapper.readTree(response.body());
                int pageCount = 0;
                for (JsonNode pr : prs) {
                    boolean isNew = upsertPullRequest(repo, pr);
                    if (isNew) counts[0]++; else counts[1]++;
                    pageCount++;
                }
                log.debug("Pull requests page processed page={} repositoryId={} prsOnPage={}", pageNum, repositoryId, pageCount);

                nextUrl = GitHubLinkHeaderParser.extractNextPageUrl(response.headers().firstValue("Link").orElse(null));
                pageNum++;
            }

            repo.setLastSyncedAt(Instant.now());
            repositoryRepo.save(repo);
            log.info("Pull requests synced repositoryId={} fullName={} pages={} created={} updated={} totalDurationMs={}",
                    repositoryId, repo.getFullName(), pageNum - 1, counts[0], counts[1],
                    System.currentTimeMillis() - totalStart);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync pull requests repositoryId={} fullName={}: {}",
                    repositoryId, repo.getFullName(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to sync pull requests: " + e.getMessage());
        }

        return prRepo.findAllByRepositoryId(repositoryId)
                .stream().map(GitHubPullRequestDto::from).toList();
    }

    /**
     * Handles an incoming pull_request webhook event.
     */
    @Transactional
    public void handlePullRequestEvent(JsonNode payload) {
        JsonNode prNode = payload.path("pull_request");
        JsonNode repoNode = payload.path("repository");
        String fullName = repoNode.path("full_name").asText();
        String action = payload.path("action").asText();
        int prNumber = prNode.path("number").asInt();

        log.debug("Handling pull_request webhook event action={} fullName={} prNumber={}", action, fullName, prNumber);

        Optional<GitHubRepository> repoOpt = repositoryRepo.findByFullName(fullName);
        if (repoOpt.isEmpty()) {
            log.debug("No tracked repository found for webhook fullName={}, skipping", fullName);
            return;
        }

        upsertPullRequest(repoOpt.get(), prNode);
        log.info("Pull request webhook processed action={} fullName={} prNumber={}", action, fullName, prNumber);
    }

    @Transactional
    boolean upsertPullRequest(GitHubRepository repo, JsonNode prNode) {
        int prNumber = prNode.path("number").asInt();
        Optional<GitHubPullRequest> existing =
                prRepo.findByRepositoryIdAndGithubPrNumber(repo.getId(), prNumber);

        boolean isNew = existing.isEmpty();
        GitHubPullRequest pr = existing.orElse(new GitHubPullRequest());
        pr.setRepository(repo);
        pr.setGithubPrNumber(prNumber);
        pr.setTitle(prNode.path("title").asText());
        pr.setBody(prNode.path("body").asText(null));
        pr.setState(prNode.path("state").asText("open"));
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
        log.debug("Pull request upserted repositoryId={} prNumber={} state={} action={}",
                repo.getId(), prNumber, pr.getState(), isNew ? "created" : "updated");
        return isNew;
    }
}
