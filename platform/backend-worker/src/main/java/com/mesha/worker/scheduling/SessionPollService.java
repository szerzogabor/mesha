package com.mesha.worker.scheduling;

import com.mesha.worker.blocks.BlocksAdapter;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult;
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
 */
@Service
class SessionPollService {

    private static final Logger log = LoggerFactory.getLogger(SessionPollService.class);

    static final Set<AIExecutionState> TERMINAL_STATES = EnumSet.of(
            AIExecutionState.DONE, AIExecutionState.FAILED, AIExecutionState.CANCELED);

    private final BlocksSessionPollerRepository sessionRepo;
    private final IssueWorkerRepository issueRepo;
    private final CommentWorkerRepository commentRepo;
    private final GitHubRepositoryWorkerRepository repoWorkerRepo;
    private final BlocksMessageWorkerRepository messageRepo;
    private final BlocksAdapter blocksAdapter;
    private final BlocksApiKeyService apiKeyService;
    private final RedisTemplate<String, String> redisTemplate;
    private final PollingProperties props;

    SessionPollService(BlocksSessionPollerRepository sessionRepo,
                       IssueWorkerRepository issueRepo,
                       CommentWorkerRepository commentRepo,
                       GitHubRepositoryWorkerRepository repoWorkerRepo,
                       BlocksMessageWorkerRepository messageRepo,
                       BlocksAdapter blocksAdapter,
                       BlocksApiKeyService apiKeyService,
                       RedisTemplate<String, String> redisTemplate,
                       PollingProperties props) {
        this.sessionRepo = sessionRepo;
        this.issueRepo = issueRepo;
        this.commentRepo = commentRepo;
        this.repoWorkerRepo = repoWorkerRepo;
        this.messageRepo = messageRepo;
        this.blocksAdapter = blocksAdapter;
        this.apiKeyService = apiKeyService;
        this.redisTemplate = redisTemplate;
        this.props = props;
    }

    /**
     * Processes a single session: applies the max-age guard first, then attempts
     * an exponential-backoff-gated poll against the Blocks API.
     *
     * @param sessionId ID of the session to process
     */
    @Transactional
    void processSession(UUID sessionId) {
        BlocksSessionRecord session = sessionRepo.findById(sessionId).orElse(null);
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

    private void dispatchSession(BlocksSessionRecord session) {
        IssueWorkerRecord issue = issueRepo.findById(session.getIssueId()).orElse(null);
        if (issue == null) {
            log.error("session_dispatch_no_issue session_id={} issue_id={}",
                    session.getId(), session.getIssueId());
            failSession(session, "Issue not found for session dispatch");
            return;
        }

        String apiKey = apiKeyService.resolveApiKey(session.getIssueId()).orElse(null);
        if (apiKey == null) {
            log.error("session_dispatch_no_api_key session_id={} issue_id={}",
                    session.getId(), session.getIssueId());
            failSession(session, "Blocks API key not configured for workspace");
            return;
        }

        List<String> comments = loadComments(session.getIssueId());
        GitHubRepositoryWorkerRecord repo = loadRepository(session.getIssueId());
        String projectName = issue.getProject() != null ? issue.getProject().getName() : null;
        String workspaceName = (issue.getProject() != null && issue.getProject().getWorkspace() != null)
                ? issue.getProject().getWorkspace().getName() : null;
        String assigneeName = issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : null;
        List<String> labelNames = issue.getLabels().stream()
                .map(LabelWorkerRecord::getName)
                .collect(Collectors.toList());

        log.info("session_dispatch_context session_id={} issue_id={} workspace={} project={} repo={} comment_count={} label_count={}",
                session.getId(), session.getIssueId(),
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
                    issue.getStatus(),
                    issue.getPriority(),
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
                    apiKey
            );
            SessionResult result = blocksAdapter.createSession(request);
            session.setProviderSessionId(result.providerSessionId());
            sessionRepo.save(session);
            log.info("session_dispatched session_id={} provider_session_id={}",
                    session.getId(), result.providerSessionId());
        } catch (HttpClientErrorException e) {
            log.error("session_dispatch_failure session_id={} issue_id={} error={}",
                    session.getId(), session.getIssueId(), e.getMessage(), e);
            failSession(session, "Blocks API rejected session creation: " + e.getMessage());
        } catch (Exception e) {
            log.error("session_dispatch_failure session_id={} issue_id={} error={}",
                    session.getId(), session.getIssueId(), e.getMessage(), e);
            session.setRetryCount(session.getRetryCount() + 1);
            sessionRepo.save(session);
        }
    }

    private List<String> loadComments(UUID issueId) {
        try {
            return commentRepo.findByIssueIdOrderByCreatedAt(issueId).stream()
                    .map(c -> {
                        String author = c.getAuthor() != null ? c.getAuthor().getDisplayName() : "Unknown";
                        return "**" + author + "** (" + c.getCreatedAt() + "):\n" + c.getBody();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("session_dispatch_comments_load_failed issue_id={} error={}", issueId, e.getMessage());
            return List.of();
        }
    }

    private GitHubRepositoryWorkerRecord loadRepository(UUID issueId) {
        try {
            List<GitHubRepositoryWorkerRecord> repos = repoWorkerRepo.findConnectedByIssueId(issueId);
            return repos.isEmpty() ? null : repos.get(0);
        } catch (Exception e) {
            log.warn("session_dispatch_repo_load_failed issue_id={} error={}", issueId, e.getMessage());
            return null;
        }
    }

    private boolean isExpired(BlocksSessionRecord session) {
        Duration age = Duration.between(session.getCreatedAt(), Instant.now());
        return age.toHours() >= props.maxSessionAgeHours();
    }

    /**
     * Uses Redis setIfAbsent as a combined distributed lock and backoff gate.
     * The TTL equals the computed backoff interval, so the session cannot be
     * polled again until that interval elapses — even across multiple instances.
     */
    private boolean acquireBackoffLock(BlocksSessionRecord session) {
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

    private void poll(BlocksSessionRecord session) {
        try {
            SessionResult result = blocksAdapter.pollSession(session.getProviderSessionId());
            AIExecutionState newState = mapToExecutionState(result.status(), session.getExecutionState());

            AIExecutionState prevState = session.getExecutionState();
            session.setRetryCount(session.getRetryCount() + 1);

            if (newState != prevState) {
                session.setExecutionState(newState);
                log.info("session_state_changed session_id={} from={} to={} provider_session_id={}",
                        session.getId(), prevState, newState, session.getProviderSessionId());
                recordStateTransitionMessage(session.getId(), newState, result.finalMessage());
            } else {
                log.debug("session_state_unchanged session_id={} state={} poll_count={}",
                        session.getId(), newState, session.getRetryCount());
            }

            sessionRepo.save(session);

        } catch (Exception e) {
            log.error("session_poll_failure session_id={} provider_session_id={} error={}",
                    session.getId(), session.getProviderSessionId(), e.getMessage(), e);
            // Let the backoff lock expire; session will be retried on next eligible cycle.
        }
    }

    private void recordStateTransitionMessage(java.util.UUID sessionId, AIExecutionState newState, String providerMessage) {
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
            BlocksMessageRecord msg = new BlocksMessageRecord();
            msg.setSessionId(sessionId);
            msg.setMessage(text);
            messageRepo.save(msg);
        } catch (Exception e) {
            log.warn("blocks_message_save_failed session_id={} state={} error={}", sessionId, newState, e.getMessage());
        }
    }

    private void failSession(BlocksSessionRecord session, String reason) {
        Duration age = Duration.between(session.getCreatedAt(), Instant.now());
        log.warn("session_expired session_id={} age_hours={} reason={}",
                session.getId(), age.toHours(), reason);
        session.setExecutionState(AIExecutionState.FAILED);
        session.setErrorMessage(reason);
        sessionRepo.save(session);
        recordStateTransitionMessage(session.getId(), AIExecutionState.FAILED, reason);
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
