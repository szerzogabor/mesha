package com.mesha.api.worker.scheduling;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.BlocksMessage;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.Comment;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Issue;
import com.mesha.api.model.Label;
import com.mesha.api.repository.BlocksMessageRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.CommentRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.repository.WorkspaceBlocksConfigRepository;
import com.mesha.api.worker.blocks.BlocksAdapter;
import com.mesha.api.worker.orchestration.SessionRequest;
import com.mesha.api.worker.orchestration.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactional helper invoked once per active session during each polling cycle.
 * Keeping per-session work in its own transaction means a failure on one session
 * never rolls back updates already applied to others.
 *
 * Operates on the API's canonical JPA entities (BlocksSession, Issue, BlocksMessage)
 * rather than the worker's lightweight record projections. The polling algorithm and
 * state-machine logic are identical to the backend-worker implementation, preserving
 * the extraction path back to a standalone service.
 */
@Service
class SessionPollService {

    private static final Logger log = LoggerFactory.getLogger(SessionPollService.class);

    static final Set<AIExecutionState> TERMINAL_STATES = EnumSet.of(
            AIExecutionState.DONE, AIExecutionState.FAILED, AIExecutionState.CANCELED);

    private final BlocksSessionRepository sessionRepo;
    private final IssueRepository issueRepo;
    private final CommentRepository commentRepo;
    private final GitHubRepositoryRepository gitHubRepoRepo;
    private final BlocksMessageRepository messageRepo;
    private final BlocksAdapter blocksAdapter;
    private final BlocksApiKeyService apiKeyService;
    private final WorkspaceBlocksConfigRepository configRepo;
    private final RedisTemplate<String, String> redisTemplate;
    private final PollingProperties props;
    private final String blocksDashboardUrl;

    SessionPollService(BlocksSessionRepository sessionRepo,
                       IssueRepository issueRepo,
                       CommentRepository commentRepo,
                       GitHubRepositoryRepository gitHubRepoRepo,
                       BlocksMessageRepository messageRepo,
                       BlocksAdapter blocksAdapter,
                       BlocksApiKeyService apiKeyService,
                       WorkspaceBlocksConfigRepository configRepo,
                       RedisTemplate<String, String> redisTemplate,
                       PollingProperties props,
                       @org.springframework.beans.factory.annotation.Value("${mesha.blocks.dashboard-url:https://www.blocks.team}") String blocksDashboardUrl) {
        this.sessionRepo = sessionRepo;
        this.issueRepo = issueRepo;
        this.commentRepo = commentRepo;
        this.gitHubRepoRepo = gitHubRepoRepo;
        this.messageRepo = messageRepo;
        this.blocksAdapter = blocksAdapter;
        this.apiKeyService = apiKeyService;
        this.configRepo = configRepo;
        this.redisTemplate = redisTemplate;
        this.props = props;
        this.blocksDashboardUrl = blocksDashboardUrl;
    }

    /**
     * Processes a single session: applies the max-age guard first, then attempts
     * an exponential-backoff-gated poll against the Blocks API.
     */
    @Transactional
    void processSession(UUID sessionId) {
        BlocksSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null || TERMINAL_STATES.contains(session.getExecutionState())) {
            return;
        }

        if (isExpired(session)) {
            failSession(session, "Session exceeded maximum age of " + props.maxSessionAgeHours() + " hours");
            return;
        }

        if (session.getProviderSessionId() == null) {
            if (session.getRetryCount() > 0 && !acquireBackoffLock(session)) {
                return;
            }
            dispatchSession(session);
            return;
        }

        if (!acquireBackoffLock(session)) {
            return;
        }

        poll(session);
    }

    private void dispatchSession(BlocksSession session) {
        UUID issueId = session.getIssue().getId();
        Issue issue = issueRepo.findById(issueId).orElse(null);
        if (issue == null) {
            log.error("session_dispatch_no_issue session_id={} issue_id={}",
                    session.getId(), issueId);
            failSession(session, "Issue not found for session dispatch");
            return;
        }

        String apiKey = apiKeyService.resolveApiKey(issueId).orElse(null);
        if (apiKey == null) {
            log.error("session_dispatch_no_api_key session_id={} issue_id={}",
                    session.getId(), issueId);
            failSession(session, "Blocks API key not configured for workspace");
            return;
        }

        String projectName = issue.getProject() != null ? issue.getProject().getName() : null;
        String workspaceName = (issue.getProject() != null && issue.getProject().getWorkspace() != null)
                ? issue.getProject().getWorkspace().getName() : null;
        UUID workspaceId = (issue.getProject() != null && issue.getProject().getWorkspace() != null)
                ? issue.getProject().getWorkspace().getId() : null;

        String assigneeName = null;
        if (issue.getAssignee() != null) {
            String name = issue.getAssignee().getName();
            assigneeName = (name != null && !name.isBlank()) ? name : issue.getAssignee().getEmail();
        }

        List<String> labelNames = issue.getLabels().stream()
                .map(Label::getName)
                .collect(Collectors.toList());

        List<String> comments = loadComments(issueId);
        GitHubRepository repo = loadRepository(workspaceId);

        log.info("session_dispatch_context session_id={} issue_id={} workspace={} project={} repo={} comment_count={} label_count={}",
                session.getId(), issueId,
                workspaceName != null ? workspaceName : "none",
                projectName != null ? projectName : "none",
                repo != null ? repo.getHtmlUrl() : "none",
                comments.size(),
                labelNames.size());

        try {
            SessionRequest request = new SessionRequest(
                    issue.getId().toString(),
                    issue.getTitle(),
                    issue.getDescription(),
                    issue.getStatus() != null ? issue.getStatus().name() : null,
                    issue.getPriority() != null ? issue.getPriority().name() : null,
                    assigneeName,
                    labelNames,
                    issue.getCreatedAt() != null ? issue.getCreatedAt().toString() : null,
                    issue.getUpdatedAt() != null ? issue.getUpdatedAt().toString() : null,
                    workspaceName,
                    projectName,
                    repo != null ? repo.getName() : null,
                    repo != null ? repo.getHtmlUrl() : null,
                    repo != null ? repo.getDefaultBranch() : null,
                    comments,
                    apiKey,
                    session.getInstructions()
            );
            SessionResult result = blocksAdapter.createSession(request);
            session.setProviderSessionId(result.providerSessionId());
            // Use session_html_url from the API if present; fall back to workspace-ID construction.
            String sessionUrl = result.sessionHtmlUrl();
            if (sessionUrl == null || sessionUrl.isBlank()) {
                String resolvedWorkspaceId = resolveAndPersistWorkspaceId(issue, result.workspaceId());
                sessionUrl = buildSessionUrl(resolvedWorkspaceId, result.providerSessionId());
            }
            session.setSessionUrl(sessionUrl);
            sessionRepo.save(session);
            log.info("session_dispatched session_id={} provider_session_id={} session_url={}",
                    session.getId(), result.providerSessionId(), sessionUrl != null ? sessionUrl : "none");
        } catch (HttpClientErrorException e) {
            log.error("session_dispatch_failure session_id={} issue_id={} error={}",
                    session.getId(), issueId, e.getMessage(), e);
            failSession(session, "Blocks API rejected session creation: " + e.getMessage());
        } catch (Exception e) {
            log.error("session_dispatch_failure session_id={} issue_id={} error={}",
                    session.getId(), issueId, e.getMessage(), e);
            session.setRetryCount(session.getRetryCount() + 1);
            sessionRepo.save(session);
        }
    }

    private List<String> loadComments(UUID issueId) {
        try {
            return commentRepo.findAllByIssueId(issueId).stream()
                    .map(c -> {
                        String author = resolveAuthorName(c);
                        return "**" + author + "** (" + c.getCreatedAt() + "):\n" + c.getBody();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("session_dispatch_comments_load_failed issue_id={} error={}", issueId, e.getMessage());
            return List.of();
        }
    }

    private String resolveAuthorName(Comment comment) {
        if (comment.getAuthor() == null) return "Unknown";
        String name = comment.getAuthor().getName();
        return (name != null && !name.isBlank()) ? name : comment.getAuthor().getEmail();
    }

    private GitHubRepository loadRepository(UUID workspaceId) {
        if (workspaceId == null) return null;
        try {
            List<GitHubRepository> repos = gitHubRepoRepo.findAllByWorkspaceIdAndConnectedTrue(workspaceId);
            return repos.isEmpty() ? null : repos.get(0);
        } catch (Exception e) {
            log.warn("session_dispatch_repo_load_failed workspace_id={} error={}", workspaceId, e.getMessage());
            return null;
        }
    }

    private boolean isExpired(BlocksSession session) {
        Duration age = Duration.between(session.getCreatedAt(), Instant.now());
        return age.toHours() >= props.maxSessionAgeHours();
    }

    /**
     * Uses Redis setIfAbsent as a combined distributed lock and backoff gate.
     * The TTL equals the computed backoff interval, so the session cannot be
     * polled again until that interval elapses — even across multiple instances.
     */
    private boolean acquireBackoffLock(BlocksSession session) {
        Duration backoff = computeBackoff(session.getRetryCount());
        String key = "mesha:session:poll:" + session.getId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", backoff);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("session_backoff_active session_id={} backoff_ms={}",
                    session.getId(), backoff.toMillis());
            return false;
        }
        return true;
    }

    private void poll(BlocksSession session) {
        try {
            SessionResult result = blocksAdapter.pollSession(session.getProviderSessionId());
            AIExecutionState newState = mapToExecutionState(result.status(), session.getExecutionState());

            AIExecutionState prevState = session.getExecutionState();
            session.setRetryCount(session.getRetryCount() + 1);

            // Prefer session_html_url returned directly by the Blocks API.
            // Fall back to workspace-ID-based construction for older sessions or when the API omits it.
            if (session.getSessionUrl() == null) {
                String sessionUrl = result.sessionHtmlUrl();
                if (sessionUrl == null || sessionUrl.isBlank()) {
                    String resolvedWorkspaceId = resolveAndPersistWorkspaceId(session.getIssue(), result.workspaceId());
                    sessionUrl = buildSessionUrl(resolvedWorkspaceId, session.getProviderSessionId());
                }
                if (sessionUrl != null) {
                    session.setSessionUrl(sessionUrl);
                    log.info("blocks_session_url_set session_id={} session_url={}", session.getId(), sessionUrl);
                }
            }

            // Fetch real agent messages from the dedicated messages endpoint.
            List<String> apiMessages = blocksAdapter.fetchAssistantMessages(session.getProviderSessionId());
            boolean hasApiMessages = apiMessages != null && !apiMessages.isEmpty();

            if (newState != prevState) {
                session.setExecutionState(newState);
                log.info("session_state_changed session_id={} from={} to={} provider_session_id={}",
                        session.getId(), prevState, newState, session.getProviderSessionId());
                if (!hasApiMessages) {
                    recordStateTransitionMessage(session, newState, result.finalMessage());
                }
            } else {
                log.debug("session_state_unchanged session_id={} state={} poll_count={}",
                        session.getId(), newState, session.getRetryCount());
            }

            if (hasApiMessages) {
                saveApiMessages(session, apiMessages);
            }

            sessionRepo.save(session);

        } catch (Exception e) {
            log.error("session_poll_failure session_id={} provider_session_id={} error={}",
                    session.getId(), session.getProviderSessionId(), e.getMessage(), e);
            // Let the backoff lock expire; session will be retried on next eligible cycle.
        }
    }

    private void saveApiMessages(BlocksSession session, List<String> apiMessages) {
        try {
            int savedApiCount = session.getApiMessageOffset();

            if (savedApiCount == 0 && messageRepo.countBySessionId(session.getId()) > 0) {
                // Discard placeholder state-transition messages now that real API messages have arrived.
                messageRepo.deleteAllBySessionId(session.getId());
                log.info("blocks_placeholder_messages_cleared session_id={}", session.getId());
            }

            int startIndex = Math.min(savedApiCount, apiMessages.size());
            List<String> newMessages = apiMessages.subList(startIndex, apiMessages.size());
            for (String text : newMessages) {
                BlocksMessage msg = new BlocksMessage();
                msg.setSession(session);
                msg.setMessage(text);
                messageRepo.save(msg);
            }
            if (!newMessages.isEmpty()) {
                session.setApiMessageOffset(savedApiCount + newMessages.size());
                log.debug("blocks_api_messages_saved session_id={} new_count={} total_api_count={}",
                        session.getId(), newMessages.size(), apiMessages.size());
            }
        } catch (Exception e) {
            log.warn("blocks_api_messages_save_failed session_id={} error={}", session.getId(), e.getMessage());
        }
    }

    private void recordStateTransitionMessage(BlocksSession session, AIExecutionState newState, String providerMessage) {
        String text = switch (newState) {
            case PLANNING   -> "Analyzing requirements and planning implementation";
            case EXECUTING  -> "Writing code and making changes";
            case WAITING_REVIEW -> "Implementation complete, opening pull request";
            case PR_OPENED  -> "Pull request created";
            case DONE       -> providerMessage != null ? providerMessage : "Implementation successfully completed";
            case FAILED     -> providerMessage != null ? "Session failed: " + providerMessage : "Session failed";
            case CANCELED   -> "Session canceled";
            default         -> null;
        };
        if (text == null) return;
        try {
            BlocksMessage msg = new BlocksMessage();
            msg.setSession(session);
            msg.setMessage(text);
            messageRepo.save(msg);
        } catch (Exception e) {
            log.warn("blocks_message_save_failed session_id={} state={} error={}", session.getId(), newState, e.getMessage());
        }
    }

    private void failSession(BlocksSession session, String reason) {
        Duration age = Duration.between(session.getCreatedAt(), Instant.now());
        log.warn("session_expired session_id={} age_hours={} reason={}",
                session.getId(), age.toHours(), reason);
        session.setExecutionState(AIExecutionState.FAILED);
        session.setErrorMessage(reason);
        sessionRepo.save(session);
        recordStateTransitionMessage(session, AIExecutionState.FAILED, reason);
    }

    /**
     * Determines the Blocks workspace ID to use for URL construction.
     * Priority: (1) returned by the Blocks API in the create-session response,
     * (2) already stored in workspace_blocks_config from a prior session.
     * When newly discovered from the API, it is persisted so future sessions use it automatically.
     */
    private String resolveAndPersistWorkspaceId(Issue issue, String fromApi) {
        if (fromApi != null && !fromApi.isBlank()) {
            UUID workspaceId = issue.getProject().getWorkspace().getId();
            int updated = configRepo.setBlocksWorkspaceIdIfAbsent(workspaceId, fromApi);
            if (updated > 0) {
                log.info("blocks_workspace_id_discovered workspace_id={} blocks_workspace_id={}", workspaceId, fromApi);
            }
            return fromApi;
        }
        // Fallback: use whatever was manually configured
        return apiKeyService.resolveBlocksWorkspaceId(issue.getId()).orElse(null);
    }

    private String buildSessionUrl(String blocksWorkspaceId, String providerSessionId) {
        if (blocksWorkspaceId == null) {
            log.warn("blocks_workspace_id_unavailable — session URL will not be set; it will be populated once the Blocks API returns it");
            return null;
        }
        String url = blocksDashboardUrl.stripTrailing() + "/app/" + blocksWorkspaceId + "/sessions/" + providerSessionId;
        log.info("blocks_session_url_created session_url={}", url);
        return url;
    }

    /**
     * Computes the backoff interval for the next poll.
     * Formula: min(baseMs × multiplier^pollCount, maxMs)
     */
    Duration computeBackoff(int pollCount) {
        double backoffMs = props.backoff().baseMs();
        for (int i = 0; i < pollCount; i++) {
            backoffMs *= props.backoff().multiplier();
            if (backoffMs >= props.backoff().maxMs()) {
                return Duration.ofMillis(props.backoff().maxMs());
            }
        }
        return Duration.ofMillis((long) Math.min(backoffMs, props.backoff().maxMs()));
    }

    /**
     * Maps a Blocks API status to the canonical AIExecutionState.
     * PENDING is treated as PLANNING on first poll (CREATED → PLANNING),
     * or left unchanged if the session is already further along.
     */
    AIExecutionState mapToExecutionState(SessionResult.SessionStatus status, AIExecutionState current) {
        return switch (status) {
            case PENDING -> current == AIExecutionState.CREATED ? AIExecutionState.PLANNING : current;
            case RUNNING -> AIExecutionState.EXECUTING;
            case COMPLETED -> AIExecutionState.DONE;
            case FAILED -> AIExecutionState.FAILED;
        };
    }
}
