package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.dto.GitHubPullRequestDto;
import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.AutomationTriggerType;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GitHubPullRequestService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestService.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final GitHubPullRequestRepository prRepo;
    private final GitHubRepositoryRepository repositoryRepo;
    private final GitHubInstallationRepository installationRepo;
    private final BlocksSessionRepository blocksSessionRepo;
    private final GitHubAppService appService;
    private final AutomationService automationService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubPullRequestService(GitHubPullRequestRepository prRepo,
                                    GitHubRepositoryRepository repositoryRepo,
                                    GitHubInstallationRepository installationRepo,
                                    BlocksSessionRepository blocksSessionRepo,
                                    GitHubAppService appService,
                                    AutomationService automationService,
                                    ObjectMapper objectMapper) {
        this.prRepo = prRepo;
        this.repositoryRepo = repositoryRepo;
        this.installationRepo = installationRepo;
        this.blocksSessionRepo = blocksSessionRepo;
        this.appService = appService;
        this.automationService = automationService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<GitHubPullRequestDto> listForRepository(UUID repositoryId, String status) {
        long startMs = System.currentTimeMillis();
        String normalizedStatus = (status == null || status.isBlank()) ? null : status.toLowerCase();
        List<GitHubPullRequestDto> prs = prRepo.findByRepositoryIdAndStatus(repositoryId, normalizedStatus)
                .stream().map(GitHubPullRequestDto::from).toList();
        log.info("Listed pull requests repositoryId={} status={} count={} durationMs={}",
                repositoryId, normalizedStatus, prs.size(), System.currentTimeMillis() - startMs);
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
    @Transactional
    public List<GitHubPullRequestDto> syncPullRequests(UUID repositoryId) {
        log.info("Syncing pull requests from GitHub repositoryId={}", repositoryId);

        GitHubRepository repo = repositoryRepo.findById(repositoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));

        GitHubInstallation installation = repo.getInstallation();
        log.debug("Fetching installation token for PR sync repositoryId={} installationId={} fullName={}",
                repositoryId, installation.getInstallationId(), repo.getFullName());
        String token = appService.getInstallationToken(installation.getInstallationId());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API + "/repos/" + repo.getFullName()
                            + "/pulls?state=all&per_page=100&sort=updated&direction=desc"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .timeout(Duration.ofSeconds(25))
                    .GET()
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - start;
            log.debug("GitHub pull requests API responded repositoryId={} fullName={} httpStatus={} durationMs={}",
                    repositoryId, repo.getFullName(), response.statusCode(), durationMs);

            Map<Integer, GitHubPullRequest> existingPrs = prRepo.findAllByRepositoryId(repositoryId)
                    .stream()
                    .collect(Collectors.toMap(GitHubPullRequest::getGithubPrNumber, Function.identity()));

            JsonNode prs = objectMapper.readTree(response.body());
            int[] counts = {0, 0};
            prs.forEach(pr -> {
                int prNumber = pr.path("number").asInt();
                boolean isNew = !existingPrs.containsKey(prNumber);
                upsertPullRequest(repo, pr, existingPrs);
                if (isNew) counts[0]++; else counts[1]++;
            });

            repo.setLastSyncedAt(Instant.now());
            repositoryRepo.save(repo);
            log.info("Pull requests synced repositoryId={} fullName={} created={} updated={} durationMs={}",
                    repositoryId, repo.getFullName(), counts[0], counts[1], durationMs);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync pull requests repositoryId={} fullName={}: {}",
                    repositoryId, repo.getFullName(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to sync pull requests: " + e.getMessage());
        }

        return prRepo.findByRepositoryIdAndStatus(repositoryId, null)
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

        GitHubPullRequest pr = upsertPullRequest(repoOpt.get(), prNode);

        AutomationTriggerType trigger = resolveAutomationTrigger(action, prNode);
        if (trigger != null && pr.getBlocksSession() != null) {
            automationService.executeFor(trigger, pr.getBlocksSession().getIssue());
        }

        log.info("Pull request webhook processed action={} fullName={} prNumber={}", action, fullName, prNumber);
    }

    private AutomationTriggerType resolveAutomationTrigger(String action, JsonNode prNode) {
        return switch (action) {
            case "opened" -> AutomationTriggerType.PR_OPENED;
            case "closed" -> prNode.hasNonNull("merged_at")
                    ? AutomationTriggerType.PR_MERGED
                    : AutomationTriggerType.PR_CLOSED;
            default -> null;
        };
    }

    private GitHubPullRequest upsertPullRequest(GitHubRepository repo, JsonNode prNode) {
        return upsertPullRequest(repo, prNode, null);
    }

    private GitHubPullRequest upsertPullRequest(GitHubRepository repo, JsonNode prNode,
            Map<Integer, GitHubPullRequest> existingPrs) {
        int prNumber = prNode.path("number").asInt();
        Optional<GitHubPullRequest> existing = existingPrs != null
                ? Optional.ofNullable(existingPrs.get(prNumber))
                : prRepo.findByRepositoryIdAndGithubPrNumber(repo.getId(), prNumber);

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

        if (pr.getBlocksSession() == null) {
            tryAutoLinkByBranch(pr);
        }
        if (pr.getBlocksSession() == null) {
            tryAutoLinkByIdentifier(pr);
        }

        pr = prRepo.save(pr);
        log.debug("Pull request upserted repositoryId={} prNumber={} state={} action={} linkedSession={}",
                repo.getId(), prNumber, pr.getState(), isNew ? "created" : "updated",
                pr.getBlocksSession() != null ? pr.getBlocksSession().getId() : "none");
        return pr;
    }

    private static final Set<AIExecutionState> TERMINAL_STATES = EnumSet.of(
            AIExecutionState.DONE, AIExecutionState.FAILED, AIExecutionState.CANCELED);
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "([A-Za-z0-9]{2,10})-(\\d+)", Pattern.CASE_INSENSITIVE);

    private void tryAutoLinkByBranch(GitHubPullRequest pr) {
        String branch = pr.getSourceBranch();
        if (branch == null || branch.isBlank()) return;
        blocksSessionRepo.findFirstByBranchName(branch).ifPresent(session -> {
            pr.setBlocksSession(session);
            advanceSessionToPrOpened(session, pr);
            log.info("auto_linked_pr_by_branch prNumber={} sessionId={} branch={}",
                    pr.getGithubPrNumber(), session.getId(), branch);
        });
    }

    private void tryAutoLinkByIdentifier(GitHubPullRequest pr) {
        if (pr.getTitle() == null || pr.getRepository() == null) return;
        UUID workspaceId = pr.getRepository().getWorkspace().getId();
        Matcher matcher = IDENTIFIER_PATTERN.matcher(pr.getTitle());
        while (matcher.find()) {
            String projectKey = matcher.group(1).toUpperCase();
            int issueNumber;
            try {
                issueNumber = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            blocksSessionRepo.findActiveSessionByProjectKeyAndIssueNumber(
                    workspaceId, projectKey, issueNumber, TERMINAL_STATES)
                .ifPresent(session -> {
                    pr.setBlocksSession(session);
                    advanceSessionToPrOpened(session, pr);
                    log.info("auto_linked_pr_by_identifier identifier={}-{} prNumber={} sessionId={}",
                            projectKey, issueNumber, pr.getGithubPrNumber(), session.getId());
                });
            if (pr.getBlocksSession() != null) break;
        }
    }

    private void advanceSessionToPrOpened(BlocksSession session, GitHubPullRequest pr) {
        if (session.getPrUrl() == null) {
            session.setPrUrl(pr.getHtmlUrl());
            session.setPrNumber(pr.getGithubPrNumber());
            session.setExecutionState(AIExecutionState.PR_OPENED);
            blocksSessionRepo.save(session);
        }
    }
}
