package com.mesha.api.worker.scheduling;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.worker.blocks.BlocksAdapter;
import com.mesha.api.worker.orchestration.SessionRequest;
import com.mesha.api.worker.orchestration.SessionResult;
import com.mesha.api.worker.scheduling.SessionPollTransactions.DispatchInputs;
import com.mesha.api.worker.scheduling.SessionPollTransactions.SessionSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates one polling cycle per active session.
 * All DB reads and writes are delegated to {@link SessionPollTransactions} in short,
 * discrete transactions so DB connections are released before any HTTP call to the
 * Blocks API — preventing connection pool exhaustion under load.
 */
@Service
class SessionPollService {

    private static final Logger log = LoggerFactory.getLogger(SessionPollService.class);

    static final Set<AIExecutionState> TERMINAL_STATES = EnumSet.of(
            AIExecutionState.DONE, AIExecutionState.FAILED, AIExecutionState.CANCELED);

    private final SessionPollTransactions txns;
    private final BlocksAdapter blocksAdapter;
    private final BlocksApiKeyService apiKeyService;
    private final RedisTemplate<String, String> redisTemplate;
    private final PollingProperties props;
    private final String blocksDashboardUrl;

    SessionPollService(SessionPollTransactions txns,
                       BlocksAdapter blocksAdapter,
                       BlocksApiKeyService apiKeyService,
                       RedisTemplate<String, String> redisTemplate,
                       PollingProperties props,
                       @org.springframework.beans.factory.annotation.Value("${mesha.blocks.dashboard-url:https://www.blocks.team}") String blocksDashboardUrl) {
        this.txns = txns;
        this.blocksAdapter = blocksAdapter;
        this.apiKeyService = apiKeyService;
        this.redisTemplate = redisTemplate;
        this.props = props;
        this.blocksDashboardUrl = blocksDashboardUrl;
    }

    /**
     * Processes a single session: applies the max-age guard first, then attempts
     * an exponential-backoff-gated poll against the Blocks API.
     * Not transactional — DB operations are handled in short transactions inside
     * {@link SessionPollTransactions} so connections are released before HTTP calls.
     */
    void processSession(UUID sessionId) {
        SessionSnapshot snapshot = txns.loadSnapshot(sessionId);
        if (snapshot == null || TERMINAL_STATES.contains(snapshot.executionState())) return;

        if (isExpired(snapshot)) {
            txns.markFailed(sessionId,
                    "Session exceeded maximum age of " + props.maxSessionAgeHours() + " hours");
            return;
        }

        if (snapshot.providerSessionId() == null) {
            if (snapshot.executionState() == AIExecutionState.DISPATCHING) {
                // Pod-crash recovery: session is stuck in DISPATCHING with no provider ID.
                // Revert to CREATED once the claim is considered stale so the next cycle can retry.
                if (isDispatchingStale(snapshot)) {
                    txns.revertStaleDispatch(sessionId);
                }
                return;
            }
            // State is CREATED — atomically claim it before touching the Blocks API.
            if (snapshot.retryCount() > 0 && !acquireBackoffLock(snapshot)) return;
            if (!txns.claimForDispatch(sessionId)) {
                log.debug("session_dispatch_claim_lost session_id={} — another worker claimed it", sessionId);
                return;
            }
            dispatchSession(sessionId);
            return;
        }

        if (!acquireBackoffLock(snapshot)) return;
        poll(snapshot);
    }

    private void dispatchSession(UUID sessionId) {
        DispatchInputs inputs = txns.loadDispatchInputs(sessionId);
        if (inputs == null) {
            txns.markFailed(sessionId, "Could not load dispatch inputs");
            return;
        }

        log.info("session_dispatch_context session_id={} issue_id={} workspace={} project={} repo={} comment_count={} label_count={}",
                sessionId, inputs.issueId(),
                inputs.workspaceName() != null ? inputs.workspaceName() : "none",
                inputs.projectName() != null ? inputs.projectName() : "none",
                inputs.repoHtmlUrl() != null ? inputs.repoHtmlUrl() : "none",
                inputs.comments().size(),
                inputs.labelNames().size());

        try {
            SessionRequest request = new SessionRequest(
                    inputs.issueId().toString(),
                    inputs.issueIdentifier(),
                    inputs.title(),
                    inputs.description(),
                    inputs.status(),
                    inputs.priority(),
                    inputs.assigneeName(),
                    inputs.labelNames(),
                    inputs.createdAt(),
                    inputs.updatedAt(),
                    inputs.workspaceName(),
                    inputs.projectName(),
                    inputs.repoName(),
                    inputs.repoHtmlUrl(),
                    inputs.repoDefaultBranch(),
                    inputs.comments(),
                    inputs.apiKey(),
                    inputs.instructions(),
                    inputs.agentLlm(),
                    inputs.blocksAgentName(),
                    inputs.agentSystemPrompt(),
                    inputs.agentStartupCommands(),
                    inputs.attachmentDescriptions()
            );
            // HTTP call — no DB connection held
            SessionResult result = blocksAdapter.createSession(request);

            String sessionUrl = result.sessionHtmlUrl();
            if (sessionUrl == null || sessionUrl.isBlank()) {
                String resolvedWorkspaceId = resolveAndPersistWorkspaceId(
                        inputs.workspaceId(), inputs.issueId(), result.workspaceId());
                sessionUrl = buildSessionUrl(resolvedWorkspaceId, result.providerSessionId());
            }

            txns.saveDispatchResult(sessionId, result.providerSessionId(), sessionUrl);
            log.info("session_dispatched session_id={} provider_session_id={} session_url={}",
                    sessionId, result.providerSessionId(), sessionUrl != null ? sessionUrl : "none");
        } catch (HttpClientErrorException e) {
            log.error("session_dispatch_failure session_id={} issue_id={} error={}",
                    sessionId, inputs.issueId(), e.getMessage(), e);
            txns.markFailed(sessionId, "Blocks API rejected session creation: " + e.getMessage());
        } catch (Exception e) {
            log.error("session_dispatch_failure session_id={} issue_id={} error={}",
                    sessionId, inputs.issueId(), e.getMessage(), e);
            txns.saveDispatchRetry(sessionId);
        }
    }

    private void poll(SessionSnapshot snapshot) {
        UUID sessionId = snapshot.sessionId();
        try {
            // HTTP calls — no DB connection held during either of these
            SessionResult result = blocksAdapter.pollSession(snapshot.providerSessionId());
            AIExecutionState newState = mapToExecutionState(result.status(), snapshot.executionState());

            String sessionUrl = null;
            if (snapshot.sessionUrl() == null) {
                sessionUrl = result.sessionHtmlUrl();
                if (sessionUrl == null || sessionUrl.isBlank()) {
                    String blocksWorkspaceId = apiKeyService
                            .resolveBlocksWorkspaceId(snapshot.issueId()).orElse(null);
                    sessionUrl = buildSessionUrl(blocksWorkspaceId, snapshot.providerSessionId());
                }
            }

            List<String> apiMessages = blocksAdapter.fetchAssistantMessages(snapshot.providerSessionId());
            boolean hasApiMessages = apiMessages != null && !apiMessages.isEmpty();
            // Clear placeholder state-transition messages only when real messages arrive for the first time
            boolean clearPlaceholders = hasApiMessages && snapshot.apiMessageOffset() == 0;
            int newApiOffset = hasApiMessages ? apiMessages.size() : snapshot.apiMessageOffset();

            txns.savePollResult(
                    sessionId,
                    snapshot.executionState(),
                    newState,
                    snapshot.retryCount() + 1,
                    sessionUrl,
                    apiMessages,
                    newApiOffset,
                    clearPlaceholders,
                    result.finalMessage());

        } catch (Exception e) {
            log.error("session_poll_failure session_id={} provider_session_id={} error={}",
                    sessionId, snapshot.providerSessionId(), e.getMessage(), e);
            // Let the backoff lock expire; session will be retried on next eligible cycle.
        }
    }

    private boolean isExpired(SessionSnapshot snapshot) {
        return Duration.between(snapshot.createdAt(), Instant.now()).toHours()
                >= props.maxSessionAgeHours();
    }

    private boolean isDispatchingStale(SessionSnapshot snapshot) {
        return Duration.between(snapshot.updatedAt(), Instant.now()).toMinutes() >= 5;
    }

    /**
     * Uses Redis setIfAbsent as a combined distributed lock and backoff gate.
     * The TTL equals the computed backoff interval, so the session cannot be
     * polled again until that interval elapses — even across multiple instances.
     */
    private boolean acquireBackoffLock(SessionSnapshot snapshot) {
        Duration backoff = computeBackoff(snapshot.retryCount());
        String key = "mesha:session:poll:" + snapshot.sessionId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", backoff);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("session_backoff_active session_id={} backoff_ms={}",
                    snapshot.sessionId(), backoff.toMillis());
            return false;
        }
        return true;
    }

    private String resolveAndPersistWorkspaceId(UUID meshaWorkspaceId, UUID issueId, String fromApi) {
        if (fromApi != null && !fromApi.isBlank()) {
            if (meshaWorkspaceId != null) {
                txns.persistBlocksWorkspaceId(meshaWorkspaceId, fromApi);
            }
            return fromApi;
        }
        return apiKeyService.resolveBlocksWorkspaceId(issueId).orElse(null);
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
            case PENDING -> (current == AIExecutionState.CREATED || current == AIExecutionState.DISPATCHING)
                    ? AIExecutionState.PLANNING : current;
            case RUNNING -> AIExecutionState.EXECUTING;
            case COMPLETED -> AIExecutionState.DONE;
            case FAILED -> AIExecutionState.FAILED;
        };
    }
}
